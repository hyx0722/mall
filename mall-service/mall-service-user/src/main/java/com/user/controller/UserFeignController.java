package com.user.controller;


import com.mall.common.web.Auths;
import com.model.bean.Product;
import com.model.bean.Result;
import com.model.exception.BusinessException;
import com.user.bean.Notification;
import com.user.bean.PublishProductRequest;
import com.user.feign.InventoryFeignClient;
import com.user.feign.ProductFeignClient;
import com.user.mapper.UserMapper;
import com.user.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
@Slf4j
@Validated
public class UserFeignController {

    @Autowired
    ProductFeignClient productFeignClient;
    @Autowired
    InventoryFeignClient inventoryFeignClient;
    @Autowired
    NotificationService notificationService;
    @Autowired
    UserMapper userMapper;

    //商家添加商品：先建商品(DB 回填主键)，再用回填后的 id 初始化库存。
    //入参为 PublishProductRequest（不含 id/userId）：上架者 id 取自登录态、商品主键由 DB 自增，
    //客户端无需也不能指定，避免越权伪造成他人商品。
    //两段远程调用任一失败即抛异常，避免“商品建好了库存却没建”的脏状态
    @PostMapping("userToAddProduct")
    public Result userToAddProduct(@RequestBody @Validated PublishProductRequest request){
        Auths.requireLogin();
        Long userId = Auths.currentUserId();
        Product product = new Product();
        product.setUserId(userId);            // 上架者 id：系统从登录态自动填充
        product.setCategoryId(request.getCategoryId());
        product.setName(request.getName().trim());
        product.setSubtitle(request.getSubtitle());
        product.setMainImage(request.getMainImage());
        product.setDetail(request.getDetail());
        product.setPrice(request.getPrice());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setStatus(1);                 // 发布即上架（0 下架/1 上架）

        Result<Product> productResult = productFeignClient.addNumProduct(product);
        if (productResult == null || productResult.getCode() != 0 || productResult.getData() == null
                || productResult.getData().getId() == null) {
            throw new BusinessException(productResult == null ? "商品服务暂不可用" : productResult.getMessage());
        }
        // 用商品服务回填的主键初始化库存
        product.setId(productResult.getData().getId());

        Result inventoryResult = inventoryFeignClient.addNumInventory(product);
        if (inventoryResult == null || inventoryResult.getCode() != 0) {
            throw new BusinessException(inventoryResult == null ? "库存服务暂不可用" : inventoryResult.getMessage());
        }
        notifySubscribersOfNewProduct(userId, product);
        return Result.success();
    }

    /**
     * 给该店订阅者群发「上新」通知。
     *
     * <p><b>尽力而为</b>：这里已经完成了两次跨服务写（商品、库存），**回滚不掉**，
     * 所以扇出失败绝不能连累上架——商品建好了却因为写通知失败而给商家报错，
     * 是比少一条通知糟糕得多的结果。故整体 try/catch 只记日志。
     *
     * <p>范围上的一处已知缺口：直接调 product 服务的 {@code POST /addNumProduct}
     * （前端 {@code api/product.js} 的 {@code addProductNum}）绕过本方法建的商品
     * **不会**产生上新通知。给 product 服务补事件或补一次跨服务回查都不划算——
     * 商店侧的四张表（订阅、公告、通知、券）本就都在 user 服务里，
     * 而这个入口是前端发布商品的唯一实际路径。
     */
    private void notifySubscribersOfNewProduct(Long sellerId, Product product) {
        try {
            notificationService.fanOut(
                    sellerId,
                    Notification.TYPE_STORE_NEW_PRODUCT,
                    storeName(sellerId) + " 上新了",
                    "「" + product.getName() + "」已上架，快来看看。",
                    Notification.REF_PRODUCT,
                    product.getId());
        } catch (Exception e) {
            log.warn("[user] 上新通知群发失败（不影响上架）sellerId={} productId={}: {}",
                    sellerId, product.getId(), e.getMessage());
        }
    }

    /** 通知文案里的店名。查不到时退回「该店铺」而不是拼出 null */
    private String storeName(Long sellerId) {
        String name = userMapper.findUsernameById(sellerId);
        return name == null ? "该店铺" : name;
    }
}
