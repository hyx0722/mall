package com.order.mapper;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 守住 order 侧三个 Mapper 的 SQL 能被正确解析——尤其是那几个 {@code @Select("<script>")} 的批量查询。
 *
 * 为什么值得单独一个测试：{@code <script>} 里的字符串会被 MyBatis 当作 **XML** 解析，
 * 于是裸的尖括号（{@code p.user_id <> #{userId}} 这种写法在普通 {@code @Select} 里完全合法）
 * 会抛 SAX 解析错误。而这个错误**只在启动期暴露**——编译通过、单测（不碰 mapper 的）通过，
 * 直到应用起不来才发现。所以「加了个批量查询然后服务起不来」是本仓很容易踩的一脚。
 *
 * 本测试利用「注册 mapper 时就会解析注解 SQL」这一点，把该错误提前到构建期：
 * 不需要数据库、不需要 Spring 上下文，也不需要 Docker——{@code DataSource} 是 mock 的，
 * 因为解析阶段根本不会取连接。
 */
class OrderMapperRegistrationTest {

    @Test
    @DisplayName("三个 Mapper 的注解 SQL（含 <script> 批量查询）都能解析")
    void mapperAnnotationsParse() throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(mock(DataSource.class));

        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        assertNotNull(sqlSessionFactory);

        // addMapper 会解析该接口上所有注解里的 SQL；任何一个 XML 转义写错都在这里抛出来
        sqlSessionFactory.getConfiguration().addMapper(OrderMapper.class);
        sqlSessionFactory.getConfiguration().addMapper(OrderItemMapper.class);
        sqlSessionFactory.getConfiguration().addMapper(ShippingMapper.class);

        assertTrue(sqlSessionFactory.getConfiguration().hasMapper(OrderMapper.class));
        assertTrue(sqlSessionFactory.getConfiguration().hasMapper(OrderItemMapper.class));
        assertTrue(sqlSessionFactory.getConfiguration().hasMapper(ShippingMapper.class));
    }
}
