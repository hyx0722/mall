package com.product.service;

import com.model.bean.Product;
import com.product.bean.ProductUpdateRequest;

public interface ProductFeinService {

    void addNumProduct(Product product);

    Product findNumProductByUserIdAndName(Long userId, String name);

    /** 商家编辑自己商品（部分更新，仅改传入字段），带归属校验 */
    void updateProduct(Long userId, ProductUpdateRequest request);

    /** 商家上/下架自己商品：status=1 上架 / 0 下架 */
    void changeProductStatus(Long userId, Long id, Integer status);

}
