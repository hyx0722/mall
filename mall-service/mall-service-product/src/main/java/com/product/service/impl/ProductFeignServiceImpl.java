package com.product.service.impl;

import com.model.bean.Product;
import com.model.exception.BusinessException;
import com.product.bean.Category;
import com.product.bean.ProductUpdateRequest;
import com.product.config.ProductCacheConfig;
import com.product.mapper.CategoryMapper;
import com.product.mapper.ProductFeignMapper;
import com.product.service.ProductFeignService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductFeignServiceImpl implements ProductFeignService {
    @Autowired
    ProductFeignMapper productFeignMapper;
    @Autowired
    CategoryMapper categoryMapper;

    @Override
    @Transactional
    @CacheEvict(cacheNames = ProductCacheConfig.C_PRODUCT_PAGE, allEntries = true)
    public void addNumProduct(Product product) {
        productFeignMapper.addNumProduct(product);
    }

    @Override
    public Product findNumProductByUserIdAndName(Long userId, String name) {
        return productFeignMapper.findNumProductByUserIdAndName(userId, name);
    }

    @Override
    @Transactional
    @Caching(evict = {
            // 改的可能是价格/名称/状态，详情必须失效——下单路径会快照这里的 price
            @CacheEvict(cacheNames = ProductCacheConfig.C_PRODUCT, key = "#r.id"),
            // 列表键是分页组合、无法枚举，只能整区清空。代价是任一商品编辑清空整个浏览缓存；
            // 这个负载下正确性优先。真在意的话正确做法是拆缓存区，而不是去猜键。
            @CacheEvict(cacheNames = ProductCacheConfig.C_PRODUCT_PAGE, allEntries = true)
    })
    public void updateProduct(Long userId, ProductUpdateRequest r) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        if (r.getId() == null) {
            throw new BusinessException("缺少商品 id");
        }
        if (r.getName() != null && r.getName().isBlank()) {
            throw new BusinessException("商品名称不能为空");
        }
        // 归属校验：必须能按 id+userId 查到自己的商品
        Product exist = productFeignMapper.findProductByIdAndUser(r.getId(), userId);
        if (exist == null) {
            throw new BusinessException("商品不存在或无权操作");
        }
        // 改名时校验同商家内不重名（uk_user_name(user_id,name) 兜底）
        if (r.getName() != null) {
            Product same = productFeignMapper.findNumProductByUserIdAndName(userId, r.getName());
            if (same != null && !same.getId().equals(r.getId())) {
                throw new BusinessException("该商品名已被占用，请换一个名称");
            }
        }
        // 改分类时校验分类存在且启用
        if (r.getCategoryId() != null) {
            Category category = categoryMapper.findById(r.getCategoryId());
            if (category == null) {
                throw new BusinessException("分类不存在");
            }
            if (category.getStatus() == null || category.getStatus() != 1) {
                throw new BusinessException("分类已停用，无法挂载商品");
            }
        }
        if (r.getStatus() != null && r.getStatus() != 0 && r.getStatus() != 1) {
            throw new BusinessException("商品状态只能为 0(下架)或 1(上架)");
        }
        int affected = productFeignMapper.updateProduct(r, userId);
        if (affected == 0) {
            throw new BusinessException("更新失败，商品不存在或无权操作");
        }
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = ProductCacheConfig.C_PRODUCT, key = "#id"),
            @CacheEvict(cacheNames = ProductCacheConfig.C_PRODUCT_PAGE, allEntries = true)
    })
    public void changeProductStatus(Long userId, Long id, Integer status) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        if (id == null) {
            throw new BusinessException("缺少商品 id");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("商品状态只能为 0(下架)或 1(上架)");
        }
        int affected = productFeignMapper.updateProductStatus(id, userId, status);
        if (affected == 0) {
            throw new BusinessException("商品不存在或无权操作");
        }
    }


}
