package com.user.service.impl;

import com.model.bean.CouponPreviewRequest;
import com.model.bean.CouponPreviewResult;
import com.model.bean.Product;
import com.model.bean.Result;
import com.model.bean.User;
import com.model.exception.BusinessException;
import com.user.bean.Coupon;
import com.user.bean.CouponCreateRequest;
import com.user.bean.CouponScope;
import com.user.bean.Notification;
import com.user.bean.UsableCouponVO;
import com.user.bean.UserCoupon;
import com.user.feign.ProductFeignClient;
import com.user.mapper.CouponMapper;
import com.user.mapper.UserCouponMapper;
import com.user.mapper.UserMapper;
import com.user.service.CouponService;
import com.user.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CouponServiceImpl implements CouponService {

    /** 金额一律两位小数、四舍五入。折扣券会产生无限小数，不显式定 scale 会算出一分钱级别的对不上账 */
    private static final int MONEY_SCALE = 2;

    @Autowired
    CouponMapper couponMapper;
    @Autowired
    UserCouponMapper userCouponMapper;
    @Autowired
    UserMapper userMapper;
    @Autowired
    ProductFeignClient productFeignClient;
    @Autowired
    NotificationService notificationService;

    // ---------- 买家 ----------

    @Override
    public List<Coupon> center() {
        return couponMapper.selectReceivable();
    }

    @Override
    @Transactional
    public void receive(Long userId, Long couponId) {
        // 第一步：条件 UPDATE 抢名额（启用中 + 有效期内 + 余量充足都由 WHERE 保证）
        if (couponMapper.tryReceive(couponId) == 0) {
            throw new BusinessException("该券已领完或不在有效期内");
        }
        // 第二步：插持有记录。重复领取会撞 uk_user_coupon——
        // 此时抛异常让整个事务回滚，第一步抢到的名额随之撤销；
        // 不能只捕获不抛，否则 received_count 会永久虚高、把别人挡在门外。
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(couponId);
        try {
            userCouponMapper.insertUserCoupon(userCoupon);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("您已领取过该券");
        }
    }

    @Override
    public List<UserCoupon> mine(Long userId, Integer status) {
        List<UserCoupon> list = userCouponMapper.selectMine(userId, status);
        if (list.isEmpty()) {
            return list;
        }
        // 批量补全券定义（与购物车「只存最小事实、读取时补全」同一做法）
        Set<Long> couponIds = list.stream().map(UserCoupon::getCouponId).collect(Collectors.toSet());
        Map<Long, Coupon> byId = couponMapper.selectByIds(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, Function.identity()));
        list.forEach(uc -> uc.setCoupon(byId.get(uc.getCouponId())));
        return list;
    }

    // ---------- 下单链路 ----------

    @Override
    public CouponPreviewResult preview(Long userId, CouponPreviewRequest request) {
        // 显式判空而不是靠 DTO 上的 @NotNull：该 DTO 要同时服务只传 lines 的 /coupon/usable
        if (request.getUserCouponId() == null) {
            return unusable("缺少券ID");
        }
        UserCoupon userCoupon = userCouponMapper.selectByIdAndUser(request.getUserCouponId(), userId);
        if (userCoupon == null) {
            return unusable("优惠券不存在");
        }
        if (userCoupon.getStatus() == null || userCoupon.getStatus() != UserCoupon.STATUS_UNUSED) {
            return unusable("该券已使用或已过期");
        }
        Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
        if (coupon == null) {
            return unusable("优惠券不存在");
        }
        return evaluate(coupon, request.getLines());
    }

    @Override
    public List<UsableCouponVO> usableCoupons(Long userId, List<CouponPreviewRequest.Line> lines) {
        List<UserCoupon> held = userCouponMapper.selectMine(userId, UserCoupon.STATUS_UNUSED);
        if (held.isEmpty()) {
            return List.of();
        }
        Set<Long> couponIds = held.stream().map(UserCoupon::getCouponId).collect(Collectors.toSet());
        Map<Long, Coupon> byId = couponMapper.selectByIds(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, Function.identity()));

        List<UsableCouponVO> result = new ArrayList<>();
        for (UserCoupon uc : held) {
            Coupon coupon = byId.get(uc.getCouponId());
            if (coupon == null) {
                continue;
            }
            // 与下单时的 preview 走**同一个** evaluate：可用性只实现一处，
            // 否则结算页显示「可用」而下单被拒（或反过来）只是时间问题
            CouponPreviewResult r = evaluate(coupon, lines);
            UsableCouponVO vo = new UsableCouponVO();
            vo.setUserCouponId(uc.getId());
            vo.setCouponId(coupon.getId());
            vo.setName(coupon.getName());
            vo.setCouponType(coupon.getCouponType());
            vo.setRule(describeRule(coupon));
            vo.setUsable(r.isUsable());
            vo.setDeduction(r.getDiscountAmount());
            vo.setReason(r.getReason());
            result.add(vo);
        }
        // 可用的排前面，再按抵扣额从大到小——用户一眼看到最划算的那张
        result.sort(Comparator.comparing(UsableCouponVO::isUsable).reversed()
                .thenComparing(vo -> vo.getDeduction() == null ? BigDecimal.ZERO : vo.getDeduction(),
                        Comparator.reverseOrder()));
        return result;
    }

    /**
     * 券对一组明细的评估结果。**preview 与 usableCoupons 共用**，
     * 保证「结算页列出的可用券」与「下单时接受的券」永远一致。
     */
    private CouponPreviewResult evaluate(Coupon coupon, List<CouponPreviewRequest.Line> lines) {
        if (coupon.getStatus() == null || coupon.getStatus() != Coupon.STATUS_ENABLED) {
            return unusable("该券已停用");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartTime()) || now.isAfter(coupon.getEndTime())) {
            return unusable("该券不在有效期内");
        }

        // 限定范围内的商品小计：没有 scope 记录即全场通用
        BigDecimal base = matchedBase(couponMapper.selectScopes(coupon.getId()), lines);
        if (base.signum() <= 0) {
            return unusable("该券不适用于本单商品");
        }

        BigDecimal discount;
        if (coupon.getCouponType() != null && coupon.getCouponType() == Coupon.TYPE_THRESHOLD) {
            if (base.compareTo(coupon.getThresholdAmount()) < 0) {
                return unusable("未满 " + plain(coupon.getThresholdAmount()) + " 元，不能使用该券");
            }
            discount = coupon.getDiscountAmount();
        } else if (coupon.getCouponType() != null && coupon.getCouponType() == Coupon.TYPE_DISCOUNT) {
            // 抵扣 = 范围内小计 × (1 - 折扣率)；再按封顶截断
            discount = base.multiply(BigDecimal.ONE.subtract(coupon.getDiscountRate()));
            if (coupon.getMaxDiscountAmount() != null
                    && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                discount = coupon.getMaxDiscountAmount();
            }
        } else {
            // 配置错误（coupon_type 不是 1/2），不往前端泄露内部细节
            log.error("[coupon] 券 {} 的类型非法: {}", coupon.getId(), coupon.getCouponType());
            return unusable("该券暂不可用");
        }

        discount = discount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        // 兜底：抵扣不得超过范围内商品金额。满减券被配成「满 10 减 100」时，
        // 少了这行会把订单总额抵成负数，等于倒找钱给买家
        if (discount.compareTo(base) > 0) {
            discount = base.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        if (discount.signum() <= 0) {
            return unusable("该券抵扣金额为 0");
        }
        return new CouponPreviewResult(true, discount, null);
    }

    /** 券的展示文案：满减写「满100减20」，折扣写「8.5折」 */
    private static String describeRule(Coupon coupon) {
        if (coupon.getCouponType() != null && coupon.getCouponType() == Coupon.TYPE_DISCOUNT) {
            BigDecimal rate = coupon.getDiscountRate() == null ? BigDecimal.ONE : coupon.getDiscountRate();
            // 0.850 -> 8.5折（乘 10 后去掉多余的 0）
            return rate.multiply(BigDecimal.TEN).stripTrailingZeros().toPlainString() + "折";
        }
        return "满" + plain(coupon.getThresholdAmount()) + "减" + plain(coupon.getDiscountAmount());
    }

    @Override
    @Transactional
    public void use(Long userId, Long userCouponId, Long orderId) {
        // 条件 UPDATE：status=0 挡住并发重复用券，user_id 挡住核销他人的券
        if (userCouponMapper.tryUse(userCouponId, userId, orderId) == 0) {
            // 走到这里说明试算与核销之间券被用掉了（同一用户并发下单），
            // 抛出让下单事务整体回滚——订单不会带着折扣落库
            throw new BusinessException("优惠券不可用，请重新下单");
        }
    }

    @Override
    @Transactional
    public void releaseByOrderId(Long orderId) {
        if (orderId == null) {
            return;
        }
        int affected = userCouponMapper.releaseByOrderId(orderId);
        if (affected == 0) {
            // 该订单没用券 / 已退过：order.canceled 与 order.refunded 都可能触发退券，必须幂等
            log.debug("[coupon] 退券未生效（订单未用券或已退过）orderId={}", orderId);
        } else {
            log.info("[coupon] 订单 {} 的券已退回", orderId);
        }
    }

    // ---------- 管理员 ----------

    @Override
    @Transactional
    public void create(CouponCreateRequest request) {
        validateCreate(request);
        doCreate(Coupon.SELLER_PLATFORM, request);
    }

    @Override
    @Transactional
    public void createForSeller(Long sellerId, CouponCreateRequest request) {
        validateCreate(request);
        List<CouponCreateRequest.Scope> scopes = request.getScopes();
        if (scopes == null || scopes.isEmpty()) {
            throw new BusinessException("商家券必须指定至少一个自己的商品");
        }
        for (CouponCreateRequest.Scope s : scopes) {
            // 分类是全平台共享的：允许商家选分类等于变相全场发券，钱却由他出
            if (s.getScopeType() == null || s.getScopeType() != CouponScope.TYPE_PRODUCT) {
                throw new BusinessException("商家券只能指定具体商品");
            }
            assertProductOwnedBy(s.getScopeId(), sellerId);
        }
        Coupon coupon = doCreate(sellerId, request);

        // 扇出「新券」通知给订阅者。**只在这里做，不在 doCreate 里做**：
        // doCreate 同时服务于管理员的平台券（seller_id=0），放进去会让每发一张平台券
        // 都按 store_id=0 广播——而「平台」不是一个店，没有任何人订阅它。
        // 放在 createForSeller 里，归属就是结构上正确的，不依赖运行时判断。
        notificationService.fanOut(
                sellerId,
                Notification.TYPE_STORE_NEW_COUPON,
                storeName(sellerId) + " 发新券了",
                "「" + coupon.getName() + "」可在本店领取。",
                Notification.REF_COUPON,
                coupon.getId());
    }

    /** 通知文案里的店名。查不到时退回「该店铺」而不是拼出 null */
    private String storeName(Long sellerId) {
        String name = userMapper.findUsernameById(sellerId);
        return name == null ? "该店铺" : name;
    }

    /**
     * 建券的公共部分。发券方由 {@code sellerId} 决定：
     * {@link Coupon#SELLER_PLATFORM} 是平台券（券中心可领），否则是该商家的店铺券（只在店铺页可领）。
     *
     * 返回建好的券（主键已由 DB 回填），供调用方拿 id 做通知的关联键。
     * **本方法不发通知**——它同时服务于平台券，扇出放在商家侧调用点，见 {@link #createForSeller}。
     */
    private Coupon doCreate(Long sellerId, CouponCreateRequest request) {
        Coupon coupon = new Coupon();
        coupon.setSellerId(sellerId);
        coupon.setName(request.getName());
        coupon.setCouponType(request.getCouponType());
        coupon.setThresholdAmount(nvl(request.getThresholdAmount()));
        coupon.setDiscountAmount(nvl(request.getDiscountAmount()));
        // 非当前类型的字段留 DDL 默认语义值，避免另一分支误读到脏数据
        coupon.setDiscountRate(request.getCouponType() == Coupon.TYPE_DISCOUNT
                ? request.getDiscountRate() : BigDecimal.ONE);
        coupon.setMaxDiscountAmount(request.getMaxDiscountAmount());
        coupon.setTotalCount(request.getTotalCount());
        coupon.setStartTime(request.getStartTime());
        coupon.setEndTime(request.getEndTime());
        coupon.setStatus(Coupon.STATUS_ENABLED);
        couponMapper.insertCoupon(coupon);

        if (request.getScopes() != null) {
            for (CouponCreateRequest.Scope s : request.getScopes()) {
                CouponScope scope = new CouponScope();
                scope.setCouponId(coupon.getId());
                scope.setScopeType(s.getScopeType());
                scope.setScopeId(s.getScopeId());
                couponMapper.insertScope(scope);
            }
        }
        return coupon;
    }

    /**
     * 校验商品归属：向 product 服务查该商品并比对 userId。
     *
     * 查不到 / 服务不可用时**必须失败**，绝不能降级放行——否则任何商家都能给别人的商品发券，
     * 用平台的成本给自己店铺引流。
     */
    private void assertProductOwnedBy(Long productId, Long sellerId) {
        Result<Product> result = productFeignClient.findProductById(productId);
        if (result == null || result.getCode() != 0 || result.getData() == null) {
            throw new BusinessException(result == null ? "商品服务暂不可用" : result.getMessage());
        }
        Product product = result.getData();
        if (product.getUserId() == null || !product.getUserId().equals(sellerId)) {
            throw new BusinessException("只能给自己的商品发券（商品 " + productId + " 不属于你）");
        }
    }

    @Override
    public void updateStatus(Long couponId, Integer status) {
        if (status == null || (status != Coupon.STATUS_ENABLED && status != Coupon.STATUS_DISABLED)) {
            throw new BusinessException("状态取值非法");
        }
        if (couponMapper.updateStatus(couponId, status) == 0) {
            throw new BusinessException("优惠券不存在");
        }
    }

    @Override
    public List<Coupon> listAll(String keyword) {
        return couponMapper.selectAll(keyword);
    }

    // ---------- 商家 ----------

    @Override
    public List<Coupon> storeCoupons(String username) {
        if (username == null || username.isBlank()) {
            return List.of();
        }
        // 店铺页是按用户名访问的（/store/:username），这里把它解析成 seller_id
        User seller = userMapper.findIdAndPasswordByUserName(username);
        if (seller == null || seller.getId() == null) {
            throw new BusinessException("商家不存在");
        }
        return couponMapper.selectReceivableBySeller(seller.getId());
    }

    @Override
    public List<Coupon> sellerCoupons(Long sellerId) {
        return couponMapper.selectBySeller(sellerId);
    }

    @Override
    public void updateStatusForSeller(Long sellerId, Long couponId, Integer status) {
        if (status == null || (status != Coupon.STATUS_ENABLED && status != Coupon.STATUS_DISABLED)) {
            throw new BusinessException("状态取值非法");
        }
        // 条件 UPDATE 带 seller_id：商家改不动别人的券
        if (couponMapper.updateStatusOwned(couponId, sellerId, status) == 0) {
            throw new BusinessException("优惠券不存在或不属于你");
        }
    }

    // ---------- 内部 ----------

    /**
     * 限定范围内的商品小计。
     * scopes 为空 = 全场通用（返回全部明细之和）；否则命中的是「商品ID 直接匹配」
     * 或「分类ID 匹配」的明细——两条是 OR 关系。
     */
    private BigDecimal matchedBase(List<CouponScope> scopes, List<CouponPreviewRequest.Line> lines) {
        if (scopes == null || scopes.isEmpty()) {
            return lines.stream()
                    .map(CouponPreviewRequest.Line::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        Set<Long> productIds = new HashSet<>();
        Set<Long> categoryIds = new HashSet<>();
        for (CouponScope s : scopes) {
            if (s.getScopeType() != null && s.getScopeType() == CouponScope.TYPE_PRODUCT) {
                productIds.add(s.getScopeId());
            } else if (s.getScopeType() != null && s.getScopeType() == CouponScope.TYPE_CATEGORY) {
                categoryIds.add(s.getScopeId());
            }
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (CouponPreviewRequest.Line line : lines) {
            boolean hit = productIds.contains(line.getProductId())
                    || (line.getCategoryId() != null && categoryIds.contains(line.getCategoryId()));
            if (hit) {
                sum = sum.add(line.getLineTotal());
            }
        }
        return sum;
    }

    private void validateCreate(CouponCreateRequest request) {
        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new BusinessException("结束时间不能早于开始时间");
        }
        Integer type = request.getCouponType();
        if (type == null || (type != Coupon.TYPE_THRESHOLD && type != Coupon.TYPE_DISCOUNT)) {
            throw new BusinessException("券类型非法");
        }
        if (type == Coupon.TYPE_THRESHOLD) {
            if (request.getThresholdAmount() == null || request.getDiscountAmount() == null) {
                throw new BusinessException("满减券必须填写门槛金额与抵扣金额");
            }
            if (request.getDiscountAmount().signum() <= 0) {
                throw new BusinessException("抵扣金额必须大于 0");
            }
        } else {
            BigDecimal rate = request.getDiscountRate();
            if (rate == null || rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
                throw new BusinessException("折扣率须在 0 与 1 之间（如 0.85 表示 8.5 折）");
            }
        }
        if (request.getTotalCount() != null && request.getTotalCount() <= 0) {
            throw new BusinessException("发放总量须大于 0");
        }
        if (request.getScopes() != null) {
            for (CouponCreateRequest.Scope s : request.getScopes()) {
                if (s.getScopeType() == null || s.getScopeId() == null
                        || (s.getScopeType() != CouponScope.TYPE_PRODUCT
                        && s.getScopeType() != CouponScope.TYPE_CATEGORY)) {
                    throw new BusinessException("适用范围非法");
                }
            }
        }
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 金额的**朴素**十进制字符串，用于拼给用户看的文案。
     *
     * ⚠️ 必须走 {@code toPlainString()}：{@code BigDecimal.stripTrailingZeros()} 会把
     * {@code 100.00} 变成 {@code 1E+2}、{@code 20.00} 变成 {@code 2E+1}，
     * 直接拼进字符串就成了「满1E+2减2E+1」——这是 stripTrailingZeros 的经典陷阱，
     * 它返回的 BigDecimal 本身是对的，错在 toString() 用了科学计数法。
     */
    private static String plain(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v.stripTrailingZeros()).toPlainString();
    }

    private static CouponPreviewResult unusable(String reason) {
        return new CouponPreviewResult(false, BigDecimal.ZERO, reason);
    }
}
