package com.order.controller;

import com.mall.common.web.Auths;
import com.model.bean.Order;
import com.model.bean.PageBean;
import com.model.bean.Result;
import com.order.bean.BuyerOrderItemVO;
import com.order.bean.CreateOrderRequest;
import com.order.bean.OrderItem;
import com.order.bean.OrderRefund;
import com.order.bean.RefundApplyRequest;
import com.order.bean.RefundAuditRequest;
import com.order.bean.ReviewCreateRequest;
import com.order.bean.ReviewReplyRequest;
import com.order.bean.ReviewStatVO;
import com.order.bean.ReviewVO;
import com.order.bean.ReviewableOrderVO;
import com.order.bean.SellerOrderVO;
import com.order.bean.ShipRequest;
import com.order.bean.Shipping;
import com.order.bean.WithdrawApplyRequest;
import com.order.service.OrderRefundService;
import com.order.service.OrderService;
import com.order.service.ProductReviewService;
import com.order.service.SettlementService;
import com.order.service.WithdrawService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
public class OrderController {
    @Autowired
    OrderService orderService;
    @Autowired
    OrderRefundService orderRefundService;
    @Autowired
    SettlementService settlementService;
    @Autowired
    WithdrawService withdrawService;
    @Autowired
    ProductReviewService productReviewService;

    // ==================== 商品评价 ====================
    //
    // 资格规则：只有**已完成**订单里的商品能评价，且每个订单每个商品一条。
    // 判定不在 controller 也不在 service——它被写进了 insertEligibleReview 的 INSERT…SELECT，
    // 见 ProductReviewMapper 的说明。
    //
    // 所有接口的 userId/sellerId 一律取自登录态，**没有任何一个收它作参数**。

    /** 某商品的评价分页。公开——商品详情页不要求登录（网关只要求 token） */
    @GetMapping("/review/list")
    public Result<PageBean<ReviewVO>> reviewList(@RequestParam Long productId,
                                                 @RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(productReviewService.pageByProduct(productId, page, size));
    }

    /** 某商品的评价汇总：平均分 / 总数 / 1-5 星分布。公开 */
    @GetMapping("/review/stat")
    public Result<ReviewStatVO> reviewStat(@RequestParam Long productId) {
        return Result.success(productReviewService.stat(productId));
    }

    /**
     * 单条评价。公开。
     * 站内通知里「商家回复了你的评价」的深链只有 reviewId，靠它换出 productId 才能跳商品页。
     */
    @GetMapping("/review/detail")
    public Result<ReviewVO> reviewDetail(@RequestParam Long id) {
        return Result.success(productReviewService.detail(id));
    }

    /** 我买过该商品、订单已完成、且尚未评价的订单列表（写评价弹框的订单选择器） */
    @GetMapping("/review/mine")
    public Result<List<ReviewableOrderVO>> reviewMine(@RequestParam Long productId) {
        Auths.requireLogin();
        return Result.success(productReviewService.reviewableOrders(Auths.currentUserId(), productId));
    }

    /** 写评价 */
    @PostMapping("/review/create")
    public Result reviewCreate(@RequestBody @Validated ReviewCreateRequest request) {
        Auths.requireLogin();
        productReviewService.create(Auths.currentUserId(), request);
        return Result.success();
    }

    /**
     * 买家视角的订单明细 + 每行能否评价（订单详情页）。
     *
     * ⚠️ 归属校验在 SQL 里（{@code o.user_id = 登录态}），不是我的订单返回空列表。
     * 命名与 {@code /admin/findOrderItems} 对齐——它是订单资源，不是评价资源。
     */
    @GetMapping("/findOrderItems")
    public Result<List<BuyerOrderItemVO>> findOrderItems(@RequestParam Long orderId) {
        Auths.requireLogin();
        return Result.success(productReviewService.orderItems(Auths.currentUserId(), orderId));
    }

    /** 商家：我商品的评价分页；{@code onlyUnreplied=true} 只列未回复的 */
    @GetMapping("/seller/reviews")
    public Result<PageBean<ReviewVO>> sellerReviews(@RequestParam(required = false) Boolean onlyUnreplied,
                                                    @RequestParam(defaultValue = "1") Integer page,
                                                    @RequestParam(defaultValue = "10") Integer size) {
        Auths.requireLogin();
        return Result.success(productReviewService.sellerReviews(Auths.currentUserId(), onlyUnreplied, page, size));
    }

    /** 商家回复评价。只有该商品的卖家能回复，且只能回复一次 */
    @PostMapping("/seller/review/reply")
    public Result sellerReviewReply(@RequestBody @Validated ReviewReplyRequest request) {
        Auths.requireLogin();
        productReviewService.reply(Auths.currentUserId(), request);
        return Result.success();
    }

    // ==================== 订单 ====================

    //查看自己所有的订单
    @GetMapping("findAllOrder")
    public Result<List<Order>> findAllOrder(){
        List<Order> allOrder = orderService.findAllOrder();
        return Result.success(allOrder);
    }
    //查看自己的某个详细订单
    @GetMapping("findDetailOrder")
    public Result<Order> findDetailOrder(Long id){
        Order detailOrder = orderService.findDetailOrder(id);
        return Result.success(detailOrder);
    }

    //下单：写入订单+明细，异步经 RabbitMQ 由库存服务扣减库存并回执状态
    @PostMapping("createOrder")
    public Result<Order> createOrder(@RequestBody @Validated CreateOrderRequest request){
        Order order = orderService.createOrder(request);
        return Result.success(order);
    }

    // ---------- 买家/商家：手动取消与商家订单（登录即可） ----------

    // 买家取消自己的待付款订单：释放锁定库存 + 关闭未付支付单（发 order.canceled）
    @PostMapping("cancel")
    public Result cancel(@RequestParam Long id) {
        Auths.requireLogin();
        orderService.buyerCancel(Auths.currentUserId(), id);
        return Result.success();
    }

    // 商家：查看含自己商品的订单（每单带本人明细与可否取消标记）
    @GetMapping("/seller/orders")
    public Result<List<SellerOrderVO>> sellerOrders() {
        Auths.requireLogin();
        return Result.success(orderService.sellerOrders(Auths.currentUserId()));
    }

    // 商家取消某个待付款订单（仅限该订单全部为本商家商品，发 order.canceled 释放库存）
    @PostMapping("/seller/cancel")
    public Result sellerCancel(@RequestParam Long id) {
        Auths.requireLogin();
        orderService.sellerCancel(Auths.currentUserId(), id);
        return Result.success();
    }

    // ---------- 发货 / 确认收货（本次补全的状态机下半段） ----------

    // 商家对自己商品所属订单发货；全部卖家都发货后整单 1待发货 -> 2待收货
    @PostMapping("/seller/ship")
    public Result sellerShip(@RequestBody @Validated ShipRequest request) {
        Auths.requireLogin();
        orderService.sellerShip(Auths.currentUserId(), request.getOrderId(),
                request.getLogisticsCompany(), request.getTrackingNo(), request.getRemark());
        return Result.success();
    }

    // 买家确认收货：整单已发货(2待收货) -> 3已完成
    @PostMapping("/receive")
    public Result receive(@RequestParam Long id) {
        Auths.requireLogin();
        orderService.buyerReceive(Auths.currentUserId(), id);
        return Result.success();
    }

    // 买家查看某订单的物流发货单（归属校验后返回）
    @GetMapping("/shippings")
    public Result<List<Shipping>> shippings(@RequestParam Long orderId) {
        Auths.requireLogin();
        return Result.success(orderService.listShippings(Auths.currentUserId(), orderId));
    }

    // 卖家对账：订单完成后生成的结算明细与各状态汇总（金额口径见 Settlement 类注释）
    @GetMapping("/seller/settlement")
    public Result sellerSettlement() {
        Auths.requireLogin();
        return Result.success(settlementService.sellerSummary(Auths.currentUserId()));
    }

    // 卖家提现：可提现余额 + 历史提现申请（账期 T+N 见 WithdrawService）
    @GetMapping("/seller/withdraw")
    public Result sellerWithdraw() {
        Auths.requireLogin();
        return Result.success(withdrawService.summary(Auths.currentUserId()));
    }

    // 发起提现申请；审核在 OrderAdminController
    @PostMapping("/seller/withdraw/apply")
    public Result withdrawApply(@RequestBody @Validated WithdrawApplyRequest request) {
        Auths.requireLogin();
        withdrawService.apply(Auths.currentUserId(), request.getAmount());
        return Result.success();
    }

    // ---------- 退款（买家申请 / 卖家审核；管理员审核在 OrderAdminController） ----------

    // 买家申请退款：待发货/待收货/已完成 -> 退款中（整单全额），等待卖家或管理员审核
    @PostMapping("/refund/apply")
    public Result<OrderRefund> refundApply(@RequestBody @Validated RefundApplyRequest request) {
        Auths.requireLogin();
        return Result.success(orderRefundService.apply(Auths.currentUserId(),
                request.getOrderId(), request.getReason()));
    }

    // 买家查看某订单的退款进度（归属校验；无申请时 data 为 null）
    @GetMapping("/refund/detail")
    public Result<OrderRefund> refundDetail(@RequestParam Long orderId) {
        Auths.requireLogin();
        return Result.success(orderRefundService.detailByOrderId(Auths.currentUserId(), orderId));
    }

    // 买家：我的全部退款申请
    @GetMapping("/refund/list")
    public Result<List<OrderRefund>> refundList() {
        Auths.requireLogin();
        return Result.success(orderRefundService.myRefunds(Auths.currentUserId()));
    }

    // 卖家：待自己审核的退款申请（仅整单商品都属于本卖家，混单归管理员）
    @GetMapping("/seller/refunds")
    public Result<List<OrderRefund>> sellerRefunds() {
        Auths.requireLogin();
        return Result.success(orderRefundService.sellerPending(Auths.currentUserId()));
    }

    // 卖家审核退款申请
    @PostMapping("/seller/refund/audit")
    public Result sellerRefundAudit(@RequestBody @Validated RefundAuditRequest request) {
        Auths.requireLogin();
        orderRefundService.audit(Auths.currentUserId(), false, request.getRefundNo(),
                request.getApprove(), request.getRejectReason());
        return Result.success();
    }

}
