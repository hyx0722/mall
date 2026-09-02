package com.product.service;

import com.model.bean.Product;
import com.product.bean.UpdateProductRequest;

public interface ProductNumService {

    void addNumProduct(Product product);

    Product findNumProductByUserIdAndName(Long userId, String name);

    /** 商家编辑自己商品（部分更新，仅改传入字段），带归属校验 */
    void updateProduct(Long userId, UpdateProductRequest request);

    /** 商家上/下架自己商品：status=1 上架 / 0 下架 */
    void changeProductStatus(Long userId, Long id, Integer status);

    /** 管理员对任意商品上/下架（无归属限制，角色校验在 controller 层） */
    void adminChangeStatus(Long id, Integer status);
}
