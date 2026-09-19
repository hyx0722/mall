package com.product.config;

import com.model.bean.PageBean;
import com.model.bean.Product;
import com.product.bean.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 守住商品缓存的值序列化往返。
 *
 * 为什么值得单独一个测试：这是缓存里**唯一一处「写入成功、读取才炸」**的失败模式。
 * RedisCache 读值时走的是无类型的 {@code deserialize(byte[])} 再强转，若序列化器没开
 * default typing，JSON 里就没有类型元数据，读回来是 LinkedHashMap，强转 PageBean 时抛
 * ClassCastException。危险在于**第一次请求（只写）完全正常**，第二次（读命中的那次）才出错——
 * 所以「curl 一次看没报错」这种验证是发现不了它的，必须显式断言读回来的**运行时类型**。
 *
 * 纯 JUnit：不需要 Redis、不需要 Spring 上下文，任何机器上都能跑。
 */
class ProductCacheSerializerTest {

    private final RedisSerializer<Object> serializer = ProductCacheConfig.valueSerializer();

    @Test
    @DisplayName("Product 往返：类型、BigDecimal 精度、LocalDateTime 都要还原")
    void productRoundTrip() {
        Product original = new Product();
        original.setId(42L);
        original.setUserId(7L);
        original.setCategoryId(3L);
        original.setName("机械键盘");
        original.setSubtitle("红轴");
        original.setMainImage("http://example.com/kb.png");
        original.setDetail("详情文本");
        original.setPrice(new BigDecimal("399.00"));
        original.setOriginalPrice(new BigDecimal("499.00"));
        original.setStatus(1);
        original.setCreatedTime(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        original.setUpdatedTime(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        original.setAvgRating(new BigDecimal("4.50"));
        original.setReviewCount(12);

        Object back = serializer.deserialize(serializer.serialize(original));

        // 这一条是本测试的核心：不开 default typing 时这里会是 LinkedHashMap
        Product restored = assertInstanceOf(Product.class, back);
        assertEquals(42L, restored.getId());
        assertEquals("机械键盘", restored.getName());
        // BigDecimal 用 compareTo 比：序列化可能改变 scale（399.00 vs 399.0）
        assertEquals(0, new BigDecimal("399.00").compareTo(restored.getPrice()));
        assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4, 5), restored.getCreatedTime());
        assertEquals(12, restored.getReviewCount());
    }

    @Test
    @DisplayName("PageBean<Product> 往返：泛型容器与元素都不能退化成 Map")
    void pageBeanRoundTrip() {
        Product p = new Product();
        p.setId(1L);
        p.setName("鼠标");
        p.setPrice(new BigDecimal("99.00"));
        PageBean<Product> original = new PageBean<>(137L, List.of(p));

        Object back = serializer.deserialize(serializer.serialize(original));

        PageBean<?> restored = assertInstanceOf(PageBean.class, back);
        assertEquals(137L, restored.getTotal());
        assertNotNull(restored.getItems());
        // 元素类型也要单独断言：容器对了不代表元素对
        Product restoredItem = assertInstanceOf(Product.class, restored.getItems().get(0));
        assertEquals("鼠标", restoredItem.getName());
    }

    @Test
    @DisplayName("Category 树往返：嵌套 children 的层级类型要保持")
    void categoryTreeRoundTrip() {
        Category leaf = new Category();
        leaf.setId(2L);
        leaf.setParentId(1L);
        leaf.setName("机械键盘");
        leaf.setSortOrder(1);
        leaf.setStatus(1);

        Category root = new Category();
        root.setId(1L);
        root.setParentId(0L);
        root.setName("外设");
        root.setSortOrder(0);
        root.setStatus(1);
        root.setCreatedTime(LocalDateTime.of(2026, 5, 6, 7, 8, 9));
        root.setChildren(new ArrayList<>(List.of(leaf)));

        Object back = serializer.deserialize(serializer.serialize(root));

        Category restored = assertInstanceOf(Category.class, back);
        assertEquals("外设", restored.getName());
        assertNotNull(restored.getChildren());
        Category restoredLeaf = assertInstanceOf(Category.class, restored.getChildren().get(0));
        assertEquals("机械键盘", restoredLeaf.getName());
        assertEquals(1L, restoredLeaf.getParentId());
        assertEquals(LocalDateTime.of(2026, 5, 6, 7, 8, 9), restored.getCreatedTime());
    }

    @Test
    @DisplayName("null 往返：不存在的商品要能缓存成 null（否则下单路径会反复穿透到库）")
    void nullRoundTrip() {
        byte[] bytes = serializer.serialize(null);
        assertNull(serializer.deserialize(bytes),
                "空值缓存失效：查不存在的商品 id 会每次都打库");
    }

    @Test
    @DisplayName("装配：三个缓存区都注册上了，改 cacheManager 时别漏掉某个")
    void cacheManagerRegistersAllRegions() {
        // 只用来构造，不会真的连 Redis（Lettuce 是惰性连接）
        RedisConnectionFactory stubConnectionFactory = mock(RedisConnectionFactory.class);

        // 必须显式转成 RedisCacheManager：@Bean 方法声明返回的是 CacheManager 接口，
        // 而 afterPropertiesSet() 在实现类上
        RedisCacheManager cacheManager =
                (RedisCacheManager) new ProductCacheConfig().cacheManager(stubConnectionFactory);
        assertNotNull(cacheManager);
        // 直接 build() 出来的实例还没走 Spring 的 InitializingBean 回调，
        // 而静态配置的缓存名是在 afterPropertiesSet() 里才登记进 configuredCaches 的。
        // 作为 @Bean 注册时这步由容器自动做，测试里必须手动补。
        cacheManager.afterPropertiesSet();
        List<String> names = new ArrayList<>(cacheManager.getCacheNames());
        assertTrue(names.contains(ProductCacheConfig.C_PRODUCT), "缺 product 缓存区: " + names);
        assertTrue(names.contains(ProductCacheConfig.C_PRODUCT_PAGE), "缺 productPage 缓存区: " + names);
        assertTrue(names.contains(ProductCacheConfig.C_CATEGORY), "缺 category 缓存区: " + names);
    }

    @Test
    @DisplayName("缓存键归一化：等价入参必须算出同一个键，否则最热接口会大面积未命中")
    void cacheKeysAreNormalized() {
        // page=null 与 page=1、sort=null 与 sort="newest"、size 超上限与上限值 —— 查的是同一份数据
        assertEquals(
                ProductCacheConfig.pageKey("手机", 3L, "newest", 1, 10),
                ProductCacheConfig.pageKey("手机", 3L, null, null, null));
        assertEquals(
                ProductCacheConfig.pageKey(null, null, "newest", 2, 100),
                ProductCacheConfig.pageKey(null, null, "newest", 2, 1000));

        // 不同页/不同关键词必须落到不同的键
        assertTrue(!ProductCacheConfig.pageKey("手机", 3L, "newest", 1, 10)
                .equals(ProductCacheConfig.pageKey("手机", 3L, "newest", 2, 10)));
        assertTrue(!ProductCacheConfig.pageKey("手机", 3L, "newest", 1, 10)
                .equals(ProductCacheConfig.pageKey("平板", 3L, "newest", 1, 10)));

        // top 分类的 parentId 归一化：null / 0 / 负数 是同一份数据
        assertEquals(ProductCacheConfig.parentKey(null), ProductCacheConfig.parentKey(-1L));
        assertEquals(ProductCacheConfig.parentKey(0L), ProductCacheConfig.parentKey(null));
    }
}
