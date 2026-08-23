package com.product.service;

import com.model.bean.Product;

public interface ProductNumService {

    void addNumProduct(Product product);

    Product findNumProduct(Product product);
}
