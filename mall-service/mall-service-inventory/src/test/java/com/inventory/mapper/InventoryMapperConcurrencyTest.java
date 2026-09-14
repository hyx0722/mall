package com.inventory.mapper;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.model.bean.Inventory;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全系统最核心的不变量：**并发扣库存绝不超卖**。
 *
 * 这条正确性不来自任何一把分布式锁，而是来自 {@link InventoryMapper#lockStock} 把
 * 「判断余量」和「扣减」压进同一条 SQL（{@code ... where available_stock >= #{qty}}），
 * 由数据库的行锁保证原子。这种正确性极其依赖具体 SQL 的写法，
 * 任何一次「顺手重构」（比如改成先 select 再判断、或把条件挪到 Java 里）都会静默破坏它，
 * 而且常规功能测试完全测不出来——只有并发测试能守住。
 *
 * 本测试用真实 MySQL（Testcontainers）：这类不变量与数据库语义强相关，
 * 用 H2 之类的内存库替代没有意义。**需要本机 Docker 守护进程处于运行状态。**
 *
 * 断言是确定性的而非「大概不超卖」：总库存 100、每次锁 5，则成功锁定次数必然**恰好**为 20。
 */
@Testcontainers
class InventoryMapperConcurrencyTest {

    private static final long PRODUCT_ID = 1L;
    private static final long SELLER_ID = 1L;
    private static final int TOTAL_STOCK = 100;
    private static final int QTY_PER_LOCK = 5;
    private static final int THREADS = 32;
    private static final int ATTEMPTS_PER_THREAD = 10;

    /** 复用各模块 resources 下的建表脚本，保证测的就是线上那份 schema */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("mall_service_inventory")
            .withUsername("mall")
            .withPassword("mall")
            .withInitScript("inventory.sql");

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUp() throws Exception {
        MysqlDataSource ds = new MysqlDataSource();
        ds.setUrl(MYSQL.getJdbcUrl());
        ds.setUser(MYSQL.getUsername());
        ds.setPassword(MYSQL.getPassword());

        DataSource dataSource = ds;
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        sqlSessionFactory = factoryBean.getObject();
        // MybatisSqlSessionFactoryBean 不会自动扫描 @Mapper 接口（生产环境由 starter 的
        // 自动配置代劳），这里必须显式登记，否则 getMapper 会抛
        // "not known to the MybatisPlusMapperRegistry"
        sqlSessionFactory.getConfiguration().addMapper(InventoryMapper.class);
    }

    /**
     * 每个用例自备数据，且各用各的 product_id：
     * JUnit 5 默认不保证方法执行顺序，用例之间不允许存在隐式依赖。
     */
    private static void seed(long productId, int totalStock, int availableStock, int lockedStock) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            Inventory seed = new Inventory();
            seed.setProductId(productId);
            seed.setUserId(SELLER_ID);
            seed.setTotalStock(totalStock);
            seed.setAvailableStock(availableStock);
            seed.setLockedStock(lockedStock);
            session.getMapper(InventoryMapper.class).updateInventory(seed);
        }
    }

    @Test
    @DisplayName("32 线程共 320 次抢锁：恰好成功 20 次，可用库存归零且不为负")
    void lockStockNeverOversells() throws Exception {
        seed(PRODUCT_ID, TOTAL_STOCK, TOTAL_STOCK, 0);

        AtomicInteger successCount = new AtomicInteger();
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        try {
            for (int t = 0; t < THREADS; t++) {
                pool.submit(() -> {
                    try {
                        startGun.await();
                        // 每个线程独占一个 SqlSession：SqlSession 非线程安全，
                        // 且这里要的正是「各自独立事务」——模拟多个下单请求同时抢锁
                        try (SqlSession session = sqlSessionFactory.openSession(true)) {
                            InventoryMapper mapper = session.getMapper(InventoryMapper.class);
                            for (int i = 0; i < ATTEMPTS_PER_THREAD; i++) {
                                if (mapper.lockStock(PRODUCT_ID, QTY_PER_LOCK) == 1) {
                                    successCount.incrementAndGet();
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finished.countDown();
                    }
                });
            }

            startGun.countDown();   // 尽量让所有线程同时开抢
            assertTrue(finished.await(60, TimeUnit.SECONDS), "并发任务未在 60s 内完成");
        } finally {
            pool.shutdownNow();
        }

        int maxPossible = TOTAL_STOCK / QTY_PER_LOCK;   // 20
        assertEquals(maxPossible, successCount.get(),
                "成功锁定次数必须恰好等于库存上限——多了就是超卖，少了说明条件 UPDATE 把能成功的也挡掉了");

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            Inventory after = session.getMapper(InventoryMapper.class).selectByProductId(PRODUCT_ID);
            assertEquals(0, after.getAvailableStock().intValue(), "可用库存应恰好归零，绝不允许为负");
            assertEquals(TOTAL_STOCK, after.getLockedStock().intValue(), "锁定库存应恰好等于总库存");
            assertEquals(TOTAL_STOCK, after.getTotalStock().intValue(), "总库存不因锁定而变动");
        }
    }

    @Test
    @DisplayName("释放锁定同样带余量条件，无法被重复释放凭空造出库存")
    void releaseLockedCannotFabricateStock() throws Exception {
        final long productId = 2L;
        seed(productId, TOTAL_STOCK, TOTAL_STOCK, 0);

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            InventoryMapper mapper = session.getMapper(InventoryMapper.class);

            assertEquals(1, mapper.lockStock(productId, TOTAL_STOCK), "先把 100 全部锁定");
            assertEquals(1, mapper.releaseLocked(productId, 60), "首次释放 60 应成功");
            assertEquals(0, mapper.releaseLocked(productId, 60),
                    "剩余锁定只有 40，再次释放 60 必须失败——否则可凭空造出库存");

            Inventory after = mapper.selectByProductId(productId);
            assertEquals(40, after.getLockedStock().intValue(), "锁定库存应只剩 40");
            assertEquals(60, after.getAvailableStock().intValue(), "可用库存应回到 60");
        }
    }
}
