package com.product.service;

import com.product.bean.CartItem;

import java.util.List;

/**
 * 购物车（Redis Hash：key=cart:{userId}，field=productId，value=数量）。
 *
 * 为什么放 product 服务：购物车展示必须补全商品名称/价格/主图，
 * 放在商品服务可以直接查库，不必为此新增一次跨服务 Feign 往返。
 */
public interface CartService {

    /** 我的购物车（实时补全商品信息，下架/已删除商品也会返回并标记 available=false） */
    List<CartItem> list(Long userId);

    /** 加购（已存在则累加），返回加购后的数量 */
    int add(Long userId, Long productId, Integer quantity);

    /** 覆盖数量；quantity <= 0 视为移除 */
    void update(Long userId, Long productId, Integer quantity);

    /** 移除单个商品 */
    void remove(Long userId, Long productId);

    /** 清空购物车 */
    void clear(Long userId);

    /** 购物车商品种类数（前端角标用） */
    int count(Long userId);

    /** 结算成功后清理已下单的商品（前端下单成功后调用） */
    void removeItems(Long userId, List<Long> productIds);
}
