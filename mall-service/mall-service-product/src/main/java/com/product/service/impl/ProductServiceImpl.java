package com.product.service.impl;



import com.model.bean.Product;
import com.model.util.ThreadLocalUtil;
import com.product.mapper.ProductMapper;
import com.product.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ProductServiceImpl implements ProductService {
    @Autowired
    ProductMapper productMapper;

    @Override
    public List<Product> findProductByProductName(Integer start, Integer size, String productName) {
        return productMapper.findProductByProductName(start-1,size,productName);
    }

    @Override
    public List<Product> findProductByUserName(Integer start,Integer size, String username){
        return productMapper.findProductByUserName(start-1,size,username);
    }

    @Override
    public List<Product> findProductByUserId(Integer start, Integer size) {
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        return  productMapper.findProductByUserId(start-1,size,userId);
    }

    @Override
    public Product findProductById(Integer id) {
        return productMapper.findProductById(id);
    }

}
