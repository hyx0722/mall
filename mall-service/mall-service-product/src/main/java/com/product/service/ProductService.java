package com.product.service;


import com.model.bean.Product;

import java.util.List;

public interface ProductService {

  List<Product> findProductByProductName(Integer start, Integer size, String productName);

  List<Product> findProductByUserName(Integer start,Integer size, String username);

  List<Product> findProductByUserId(Integer start,Integer size);

  Product findProductById(Integer id);
}


