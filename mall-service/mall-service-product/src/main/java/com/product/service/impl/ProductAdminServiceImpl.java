package com.product.service.impl;

import com.model.bean.PageBean;
import com.model.bean.Product;
import com.model.exception.BusinessException;
import com.product.mapper.ProductAdminMapper;
import com.product.service.ProductAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
@Service
public class ProductAdminServiceImpl implements ProductAdminService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    @Autowired
    ProductAdminMapper productAdminMapper;

    private int normSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    @Override
    public PageBean<Product> pageAllProducts(Integer page, Integer size, String keyword) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = normSize(size);
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        long total = productAdminMapper.countAllProducts(kw);
        if (total == 0L) {
            return new PageBean<>(0L, List.of());
        }
        List<Product> items = productAdminMapper.pageAllProducts(kw, (p - 1) * s, s);
        return new PageBean<>(total, items);
    }

    @Override
    @Transactional
    public void adminChangeStatus(Long id, Integer status) {
        if (id == null) {
            throw new BusinessException("缺少商品 id");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("商品状态只能为 0(下架)或 1(上架)");
        }
        int affected = productAdminMapper.updateStatusById(id, status);
        if (affected == 0) {
            throw new BusinessException("商品不存在");
        }
    }
}
