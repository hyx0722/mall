package com.product.service.impl;

import com.model.bean.Product;
import com.product.mapper.ProductNumMapper;
import com.product.service.ProductNumService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProductNumServiceImpl implements ProductNumService {
    @Autowired
    ProductNumMapper productNumMapper;

    @Override
    public void addProduct(Product product) {
        productNumMapper.addProduct(product);
    }
}
