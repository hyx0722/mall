package com.product.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 商品/分类读路径的缓存配置。
 *
 * 本类只被 product 服务使用（{@code mall-service-product/pom.xml} 是唯一声明
 * spring-boot-starter-cache 的模块）——其余四个服务没有任何 {@code @Cacheable}，
 * 给它们引入 CacheManager 只会白白多一个 Redis 依赖面。
 *
 * ── 缓存能安全存在的前提（改这个类前请先读完）──────────────────────────
 * <ol>
 *   <li><b>product 表没有库存列</b>。库存独立在 mall_service_inventory 库，由 inventory 服务
 *       经条件 UPDATE 维护。所以缓存 Product 行**不可能**缓存出陈旧库存——这一点是本配置
 *       敢缓存商品详情的基础。（若日后把库存挪进 product 表，本配置必须重新评估。）</li>
 *   <li><b>Product.userId 不可变</b>。所有 update 语句都不含 user_id，故缓存行不可能呈现
 *       过期的归属信息。user 服务的 {@code CouponServiceImpl.assertProductOwnedBy} 拿它做
 *       发券归属校验（fail-closed），该不变量一旦被破坏，这里就从性能优化变成越权漏洞。</li>
 *   <li><b>product 无删除路径、id 为 AUTO_INCREMENT 不复用</b>。所以缓存「不存在的 id → null」
 *       是安全的，null 不可能掩盖一个后来才存在的行。正因如此才开启空值缓存：
 *       关掉它会让下单路径（每行商品调一次 findProductById）的不存在 id 每次都穿透到库。
 *       不要以「防缓存穿透」为由关掉空值缓存。</li>
 * </ol>
 *
 * ── 已知边界 ───────────────────────────────────────────────────────────
 * {@code @CacheEvict} 在方法返回时执行，**可能早于**外层 {@code @Transactional} 提交。
 * 这个缝隙里的并发读会用提交前的行回填缓存，直到 TTL 过期才纠正。这是 Spring Cache 的标准
 * 取舍，由 60s 的 TTL 兜底。要做到无缝隙需改为注册 {@code TransactionSynchronization.afterCommit}
 * 的失效器——本轮不做，但把选择留在这里让后人可见。
 *
 * 另外 avgRating / reviewCount 来自跨库子查询（mall_service_order.product_review），
 * 评价由 order 服务写入，product 服务没有任何事件能感知。故列表缓存 TTL 取短（60s），
 * 星级最多滞后 60 秒。没有为此引入 RabbitMQ 消费者——那需要新依赖 + 队列声明 + DLQ +
 * 幂等/重放考量，成本远超收益。
 */
@Configuration
public class ProductCacheConfig implements CachingConfigurer {

    public static final String C_PRODUCT = "product";
    public static final String C_PRODUCT_PAGE = "productPage";
    public static final String C_CATEGORY = "category";

    /** 与 ProductServiceImpl 的归一化保持同一套取值；服务实现直接调这里的静态方法，不存在两份 */
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    // ────────────────────────── 归一化 + 缓存键 ──────────────────────────
    //
    // 为什么键要用静态方法而不是内联 SpEL：
    // @Cacheable 的 key 在**方法体之前**算出来，方法自身的归一化根本还没跑。若裸拼
    // "#page + ':' + #size"，那么 page=null 与 page=1、sort=null 与 sort="newest"、
    // size=1000 与 size=100 各自会生成不同的键，而它们查的是**同一份数据**——
    // 在最热的 /product/list 上会造成永久性的约三分之二未命中。
    // 所以键必须施加与方法体完全相同的归一化，且两者共用下面这几个方法，杜绝漂移。

    public static int normPage(Integer page) {
        return (page == null || page < 1) ? 1 : page;
    }

    public static int normSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    public static String normSort(String sort) {
        return (sort == null || sort.isBlank()) ? "newest" : sort;
    }

    /** 老接口的 start 按 1 起页码处理：offset=(page-1)*size */
    public static int toOffset(Integer start, Integer size) {
        return (normPage(start) - 1) * normSize(size);
    }

    /** {@code ProductServiceImpl.findProductPage} 的键：五个入参归一化后的组合 */
    public static String pageKey(String keyword, Long categoryId, String sort, Integer page, Integer size) {
        return categoryId + ":" + normSort(sort) + ":" + normPage(page) + ":" + normSize(size)
                + ":" + esc(keyword);
    }

    /** {@code ProductServiceImpl.findProductByUserName} 的键（该接口用 start/size 而非 page/size） */
    public static String userNameKey(Integer start, Integer size, String username) {
        return normPage(start) + ":" + normSize(size) + ":" + esc(username);
    }

    /** 顶层分类的 parentId 约定为 0；null 与负数都归 0（与 CategoryServiceImpl 一致） */
    public static long normParentId(Long parentId) {
        return (parentId == null || parentId < 0) ? 0L : parentId;
    }

    /**
     * {@code CategoryServiceImpl.listEnabledByParent} 的键。
     * 同样要归一化：null / 0 / -1 查的是同一份数据，不归一化会白白多出两个键。
     */
    public static String parentKey(Long parentId) {
        return "parent:" + normParentId(parentId);
    }

    /**
     * 转义自由文本，避免它拼出的内容被误读成别的字段（如关键词里含分隔符）。
     * URLEncoder 是单射的，足以消除歧义；可读性差点无所谓，键本来就只是给 Redis 看的。
     */
    private static String esc(String raw) {
        return raw == null ? "" : URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }

    // ────────────────────────── 装配 ──────────────────────────

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer()))
                // 与 login:token: / cart: 等既有键空间隔离
                .prefixCacheNameWith("mall:cache:")
                .entryTtl(Duration.ofSeconds(60));

        return RedisCacheManager.builder(meteredWriter(connectionFactory))
                .cacheDefaults(base)
                // 分类是整表读 + 内存建树，且只被两个管理端方法改动，可以放长
                .withCacheConfiguration(C_CATEGORY, base.entryTtl(Duration.ofHours(1)))
                // 商品详情取 60s 而非更长：下单路径会同步快照这里的 price，
                // 改价靠 updateProduct 的 evict 立即生效，TTL 只是兜底。调大前请重读
                // OrderServiceImpl.createOrder。
                .withCacheConfiguration(C_PRODUCT, base.entryTtl(Duration.ofSeconds(60)))
                .withCacheConfiguration(C_PRODUCT_PAGE, base.entryTtl(Duration.ofSeconds(60)))
                .build();
    }

    /**
     * 打开统计收集的 RedisCacheWriter——**不开的话 cache.gets / cache.puts 恒为 0**。
     *
     * 为什么需要显式打开：Boot 的缓存指标（{@code RedisCacheMetrics}）是从
     * {@code RedisCache.getStatistics()} 读 hits/misses/puts 的，而 Spring Data Redis 的
     * {@code DefaultRedisCacheWriter} **默认用 {@code CacheStatisticsCollector.none()}**，
     * 也就是一个空实现。结果是：缓存本身工作得好好的，指标却永远是 0 ——
     * 这比没有指标更糟，因为它会让人误以为缓存从未命中。
     *
     * 为什么用 {@code create(...)} 而不是更直的 {@code nonLockingRedisCacheWriter(factory)}：
     * 后者没有收集器参数。已逐项核对过 {@code DefaultRedisCacheWriterConfigurer} 的默认值——
     * batchStrategy={@code BatchStrategies.keys()}、无加锁、immediateWrites=false，
     * 与 {@code nonLockingRedisCacheWriter(factory)} 完全一致，故这里是**纯增量**：
     * 除了多一个收集器，写入语义一字未改。
     *
     * 收集器是 {@code DefaultCacheStatisticsCollector}，一个内存 Map + 计数自增，
     * 不产生额外的 Redis 往返，也没有定时任务，开销可忽略。
     *
     * ⚠️ 统计是**按应用实例**计的（各实例只看得见自己的命中），不做跨实例聚合。
     * 这对 Prometheus 反而是对的——各实例各自暴露、由 Prometheus 求和；
     * 但别拿单个实例的数字去推断整个集群的命中率。
     */
    private RedisCacheWriter meteredWriter(RedisConnectionFactory connectionFactory) {
        return RedisCacheWriter.create(connectionFactory, cfg -> cfg.collectStatistics());
    }

    /**
     * 值序列化器。**必须**开 default typing，两个独立的理由：
     * <ol>
     *   <li><b>类型还原</b>：RedisCache 走无类型的 {@code deserialize(byte[])} 再强转。
     *       不开 default typing 时 JSON 里没有类型元数据，读回来是 LinkedHashMap，
     *       强转 PageBean 直接 ClassCastException。注意它**只在读路径爆**——
     *       只写不读的验证（比如只 curl 一次）是发现不了的，第二次请求才现形。</li>
     *   <li><b>空值缓存</b>：缓存不存在的 id 需要 NullValueSerializer 的标记，
     *       由 enableSpringCacheNullValueSupport 提供（配合 CacheConfiguration 默认的
     *       allowCacheNullValues=true）。缺了它，每次查不存在的商品都会穿透到库。</li>
     * </ol>
     * 用的是 Jackson 3 版（类名无 "2"）——本仓由 Boot 4 托管 Jackson 3（tools.jackson），
     * 用错成 Jackson 2 那版会拿不到 tools.jackson 的对象。
     *
     * 包级可见（而非 private）是刻意的：{@code ProductCacheSerializerTest} 直接拿它做
     * 序列化往返断言。那是本配置里唯一「写成功但读才炸」的部分，值得单独守着。
     */
    static RedisSerializer<Object> valueSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
                // 白名单只放行本服务会缓存的类型。三者都是实测必需的，收窄任何一条都会在
                // **读路径**（而非写路径）炸，别凭直觉删：
                //   java.util  —— PageBean.items / Category.children 实际是 ArrayList，
                //                 Map 则是 LinkedHashMap；
                //   java.math  —— Product.price / originalPrice / avgRating 是 BigDecimal，
                //                 Jackson 会给它写类型 id，不放行则读取时被
                //                 PolymorphicTypeValidator 拒绝：
                //                 "Could not resolve type id 'java.math.BigDecimal'"。
                //                 （Long/String/Integer 属 natural type，不带类型 id，无需放行。）
                // 其余一律拒绝：这份白名单是反序列化 gadget 的主要防线，不要图省事写成 java.。
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.product.")
                        .allowIfSubType("com.model.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.math.")
                        .build())
                .enableSpringCacheNullValueSupport()
                // Jackson 3 把 java.time 支持内置在 databind 里（没有单独的 jsr310 模块），
                // 但序列化器用的是它自己的私有 mapper，故显式加载模块，
                // 否则 Product/Category 上的 LocalDateTime 字段序列化不出预期格式。
                .customize(b -> b.findAndAddModules())
                .build();
    }

    /**
     * 缓存故障不能升级成业务故障。Spring 默认的 SimpleCacheErrorHandler 会**重抛**：
     * Redis 连不上时公开的 /product/list、/category/tree 会直接 500。
     *
     * 本仓恰好埋着这个雷——docker-compose 把 Redis 映射到宿主机 6380，而各服务的
     * application-datasource.yml 都写 6379，真实值在 Nacos。配错一次就是这个后果。
     * 用 Spring 自带的 LoggingCacheErrorHandler（它记日志后**吞掉**异常，不重抛），
     * 失败时视作未命中继续走库，接口仍可用。
     *
     * 参数 false = 不打印调用栈，避免 Redis 长时间不可用把日志刷爆。
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(false);
    }
}
