package com.product.service;


import com.model.bean.PageBean;
import com.model.bean.Product;

import java.util.List;

public interface ProductService {

  List<Product> findProductByProductName(Integer start, Integer size, String productName);

  List<Product> findProductByUserName(Integer start,Integer size, String username);

  List<Product> findProductByUserId(Integer start,Integer size);

  Product findProductById(Long id);

  /** 买家浏览：关键词模糊 + 分类筛选 + 白名单排序 + 分页，返回总数（page 从 1 起） */
  PageBean<Product> findProductPage(String keyword, Long categoryId, String sort, Integer page, Integer size);


}


