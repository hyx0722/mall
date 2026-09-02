package com.product.service.impl;

import com.model.bean.Product;
import com.product.mapper.ProductNumMapper;
import com.product.service.ProductNumService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductNumServiceImpl implements ProductNumService {
    @Autowired
    ProductNumMapper productNumMapper;

    @Override
    @Transactional
    public void addNumProduct(Product product) {
        productNumMapper.addNumProduct(product);
    }

    @Override
    public Product findNumProductByUserIdAndName(Integer userId, String name) {
        return productNumMapper.findNumProductByUserIdAndName(userId, name);
    }
}
