package com.product.service.impl;


import com.model.bean.PageBean;
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

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    @Autowired
    ProductMapper productMapper;

    @Override
    public List<Product> findProductByProductName(Integer start, Integer size, String productName) {
        // 老接口转调统一的浏览分页（按名精确匹配兼容：把 productName 作为 keyword 模糊命中）
        return findProductPage(productName, null, "newest", start, size).getItems();
    }

    @Override
    public List<Product> findProductByUserName(Integer start,Integer size, String username){
        return productMapper.findProductByUserName(toOffset(start, size), normSize(size), username);
    }

    @Override
    public List<Product> findProductByUserId(Integer start, Integer size) {
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        return  productMapper.findProductByUserId(toOffset(start, size), normSize(size), userId);
    }

    @Override
    public Product findProductById(Integer id) {
        return productMapper.findProductById(id);
    }

    @Override
    public PageBean<Product> findProductPage(String keyword, Integer categoryId, String sort, Integer page, Integer size) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = normSize(size);
        String order = (sort == null || sort.isBlank()) ? "newest" : sort;

        long total = productMapper.countProductList(keyword, categoryId);
        if (total == 0L) {
            return new PageBean<>(0L, List.of());
        }
        List<Product> items = productMapper.findProductList(keyword, categoryId, order, (p - 1) * s, s);
        return new PageBean<>(total, items);
    }

    /** 老接口的 start 按 1 起页码处理：offset=(start-1)*size */
    private int toOffset(Integer start, Integer size) {
        int page = (start == null || start < 1) ? 1 : start;
        return (page - 1) * normSize(size);
    }

    private int normSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
