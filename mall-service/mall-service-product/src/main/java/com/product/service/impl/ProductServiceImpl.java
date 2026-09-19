package com.product.service.impl;


import com.mall.common.web.Auths;
import com.model.bean.PageBean;
import com.model.bean.Product;
import com.product.config.ProductCacheConfig;
import com.product.mapper.ProductMapper;
import com.product.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    ProductMapper productMapper;

    /**
     * ⚠️ 本方法通过 {@code this.} 调用 {@link #findProductPage}，**自调用绕过代理**，
     * 因此拿不到那边的缓存。这是已知且可接受的（老接口流量低），不要用自注入去「修」它——
     * 那会把一个只读服务变成需要 AopContext 暴露代理的写法，代价远大于收益。
     */
    @Override
    public List<Product> findProductByProductName(Integer start, Integer size, String productName) {
        // 老接口转调统一的浏览分页（按名精确匹配兼容：把 productName 作为 keyword 模糊命中）
        return findProductPage(productName, null, "newest", start, size).getItems();
    }

    @Override
    @Cacheable(cacheNames = ProductCacheConfig.C_PRODUCT_PAGE,
            key = "T(com.product.config.ProductCacheConfig).userNameKey(#start,#size,#username)")
    public List<Product> findProductByUserName(Integer start,Integer size, String username){
        return productMapper.findProductByUserName(
                ProductCacheConfig.toOffset(start, size), ProductCacheConfig.normSize(size), username);
    }

    /** 不缓存：按登录用户取，复用率低；且结果含未上架行，与公开列表的语义不同 */
    @Override
    public PageBean<Product> findProductByUserId(Integer start, Integer size) {
        Auths.requireLogin();
        Long userId = Auths.currentUserId();
        int s = ProductCacheConfig.normSize(size);
        // 带 total 返回，前端才能算出总页数并据此禁用「下一页」（否则末页点下一页只会得到空列表）
        long total = productMapper.countProductByUserId(userId);
        if (total == 0L) {
            return new PageBean<>(0L, List.of());
        }
        List<Product> items = productMapper.findProductByUserId(ProductCacheConfig.toOffset(start, size), s, userId);
        return new PageBean<>(total, items);
    }

    /**
     * 单个商品详情。同时被下单路径（order 每行商品一次 Feign 同步快照价格）和
     * user 服务的发券归属校验使用，是本服务最值得缓存的一处。
     *
     * 不存在的 id 会缓存 null —— 这是**有意**的，安全性依赖「product 无删除路径、id 不复用」，
     * 详见 ProductCacheConfig 的类注释。别以「防缓存穿透」为由关掉它。
     */
    @Override
    @Cacheable(cacheNames = ProductCacheConfig.C_PRODUCT, key = "#id")
    public Product findProductById(Long id) {
        return productMapper.findProductById(id);
    }

    @Override
    @Cacheable(cacheNames = ProductCacheConfig.C_PRODUCT_PAGE,
            key = "T(com.product.config.ProductCacheConfig).pageKey(#keyword,#categoryId,#sort,#page,#size)")
    public PageBean<Product> findProductPage(String keyword, Long categoryId, String sort, Integer page, Integer size) {
        // 归一化统一走 ProductCacheConfig 的静态方法：缓存键由同一组方法算出，
        // 两边共用一份实现，就不存在「改了这里忘了改键」的漂移。
        int p = ProductCacheConfig.normPage(page);
        int s = ProductCacheConfig.normSize(size);
        String order = ProductCacheConfig.normSort(sort);

        long total = productMapper.countProductList(keyword, categoryId);
        if (total == 0L) {
            return new PageBean<>(0L, List.of());
        }
        List<Product> items = productMapper.findProductList(keyword, categoryId, order, (p - 1) * s, s);
        return new PageBean<>(total, items);
    }
}
