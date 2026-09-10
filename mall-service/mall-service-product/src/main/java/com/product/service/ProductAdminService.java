package com.product.service;

import com.model.bean.PageBean;
import com.model.bean.Product;

public interface ProductAdminService {
    /** 管理员：分页查看所有商品（含下架，带卖家用户名），关键词匹配商品名 */
    PageBean<Product> pageAllProducts(Integer page, Integer size, String keyword);

    /** 管理员对任意商品上/下架（无归属限制，角色校验在 controller 层） */
    void adminChangeStatus(Long id, Integer status);
}
