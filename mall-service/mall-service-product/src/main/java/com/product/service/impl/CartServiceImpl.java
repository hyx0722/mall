package com.product.service.impl;

import com.model.bean.Product;
import com.model.exception.BusinessException;
import com.product.bean.CartItem;
import com.product.mapper.ProductMapper;
import com.product.service.CartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 购物车实现：Redis Hash 存最小事实（productId -> 数量），展示信息实时回查商品表。
 *
 * 单件商品加购上限 {@link #MAX_QUANTITY}，防止误操作把数量刷到离谱值
 * （真正的超卖防护在下单链路，这里只是体验层面的护栏）。
 */
@Service
@Slf4j
public class CartServiceImpl implements CartService {

    private static final String KEY_PREFIX = "cart:";
    private static final int MAX_QUANTITY = 999;

    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    ProductMapper productMapper;

    @Override
    public List<CartItem> list(Long userId) {
        String key = key(userId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return new ArrayList<>();
        }
        // 收集商品 id，一次性批量补全商品信息（避免逐条查库）
        Set<Long> productIds = new LinkedHashSet<>();
        for (Object field : entries.keySet()) {
            Long pid = parseLong(field);
            if (pid != null) {
                productIds.add(pid);
            }
        }
        Map<Long, Product> productMap = new HashMap<>();
        if (!productIds.isEmpty()) {
            for (Product p : productMapper.findByIds(productIds)) {
                productMap.put(p.getId(), p);
            }
        }

        List<CartItem> items = new ArrayList<>();
        for (Map.Entry<Object, Object> e : entries.entrySet()) {
            Long productId = parseLong(e.getKey());
            Integer quantity = parseInt(e.getValue());
            if (productId == null || quantity == null) {
                continue;
            }
            CartItem item = new CartItem();
            item.setProductId(productId);
            item.setQuantity(quantity);
            Product product = productMap.get(productId);
            if (product == null) {
                // 商品已被删除：保留条目让用户能看见并自行移除
                item.setAvailable(false);
            } else {
                item.setName(product.getName());
                item.setSubtitle(product.getSubtitle());
                item.setMainImage(product.getMainImage());
                item.setPrice(product.getPrice());
                item.setStatus(product.getStatus());
                item.setSellerId(product.getUserId());
                item.setAvailable(product.getStatus() != null && product.getStatus() == 1);
                if (product.getPrice() != null) {
                    item.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
                }
            }
            items.add(item);
        }
        return items;
    }

    @Override
    public int add(Long userId, Long productId, Integer quantity) {
        requireUser(userId);
        if (productId == null) {
            throw new BusinessException("缺少商品 id");
        }
        // 加购前校验商品可买，避免把已下架商品塞进车里
        Product product = productMapper.findProductById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        if (product.getStatus() != null && product.getStatus() == 0) {
            throw new BusinessException("商品已下架");
        }
        int delta = (quantity == null || quantity <= 0) ? 1 : quantity;

        String key = key(userId);
        String field = String.valueOf(productId);
        Long after = redisTemplate.opsForHash().increment(key, field, delta);
        if (after != null && after > MAX_QUANTITY) {
            redisTemplate.opsForHash().put(key, field, String.valueOf(MAX_QUANTITY));
            return MAX_QUANTITY;
        }
        return after == null ? delta : after.intValue();
    }

    @Override
    public void update(Long userId, Long productId, Integer quantity) {
        requireUser(userId);
        if (productId == null) {
            throw new BusinessException("缺少商品 id");
        }
        if (quantity == null || quantity <= 0) {
            remove(userId, productId);
            return;
        }
        redisTemplate.opsForHash().put(key(userId), String.valueOf(productId),
                String.valueOf(Math.min(quantity, MAX_QUANTITY)));
    }

    @Override
    public void remove(Long userId, Long productId) {
        requireUser(userId);
        if (productId == null) {
            throw new BusinessException("缺少商品 id");
        }
        redisTemplate.opsForHash().delete(key(userId), String.valueOf(productId));
    }

    @Override
    public void clear(Long userId) {
        requireUser(userId);
        redisTemplate.delete(key(userId));
    }

    @Override
    public int count(Long userId) {
        if (userId == null) {
            return 0;
        }
        Long size = redisTemplate.opsForHash().size(key(userId));
        return size == null ? 0 : size.intValue();
    }

    @Override
    public void removeItems(Long userId, List<Long> productIds) {
        requireUser(userId);
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        Object[] fields = productIds.stream().map(String::valueOf).toArray();
        redisTemplate.opsForHash().delete(key(userId), fields);
        log.info("[product] 购物车已清理下单成功的 {} 个商品 userId={}", fields.length, userId);
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
    }

    private Long parseLong(Object v) {
        try {
            return v == null ? null : Long.valueOf(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(Object v) {
        try {
            return v == null ? null : Integer.valueOf(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
