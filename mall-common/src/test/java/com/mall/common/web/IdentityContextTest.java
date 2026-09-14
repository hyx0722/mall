package com.mall.common.web;

import com.model.exception.BusinessException;
import com.model.util.ThreadLocalUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 身份上下文（ThreadLocal）的写入、读取与**清理**。
 *
 * 清理那一条尤其关键：Web 容器的线程会被复用，一旦 afterCompletion 漏掉 remove()，
 * 下一个请求就会「继承」上一个请求的用户身份——表现为随机、难复现的越权。
 * 这类 bug 靠人工点页面基本测不出来，只能由测试守住。
 */
class IdentityContextTest {

    @AfterEach
    void tearDown() {
        // 测试之间互不污染：ThreadLocal 是静态的
        ThreadLocalUtil.remove();
    }

    @Test
    @DisplayName("网关注入的身份头写入上下文，Auths 可读且管理员断言通过")
    void interceptorPopulatesIdentity() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "42");
        request.addHeader("X-Username", "alice");
        request.addHeader("X-User-Role", String.valueOf(Auths.ROLE_ADMIN));

        new IdentityInterceptor().preHandle(request, new MockHttpServletResponse(), new Object());

        assertEquals(42L, Auths.currentUserId());
        assertEquals(Auths.ROLE_ADMIN, Auths.currentRole());
        Auths.requireAdmin();   // 不抛即通过
    }

    @Test
    @DisplayName("请求结束后身份必须被清除，避免线程池复用造成身份残留")
    void interceptorClearsIdentityAfterCompletion() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "42");
        request.addHeader("X-User-Role", String.valueOf(Auths.ROLE_ADMIN));

        IdentityInterceptor interceptor = new IdentityInterceptor();
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        assertEquals(42L, Auths.currentUserId());

        interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);

        assertNull(Auths.currentUserId(), "afterCompletion 必须调用 ThreadLocalUtil.remove()");
        assertNull(Auths.currentRole(), "角色也必须一并清除");
    }

    @Test
    @DisplayName("缺少身份头时放行且不写上下文（内部 Feign / 白名单路径）")
    void missingHeaderPassesThroughWithoutIdentity() throws Exception {
        boolean allowed = new IdentityInterceptor()
                .preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

        assertTrue(allowed, "无身份头应放行，鉴权由网关负责");
        assertNull(Auths.currentUserId());
        assertNull(Auths.currentRole());
    }

    @Test
    @DisplayName("未登录时 requireLogin / requireAdmin 均拒绝")
    void guardsRejectWhenNotLoggedIn() {
        assertThrows(BusinessException.class, Auths::requireLogin);
        assertThrows(BusinessException.class, Auths::requireAdmin);
    }

    @Test
    @DisplayName("普通用户不得通过管理员断言，但本人身份仍可读")
    void normalUserCannotPassAdminGuard() {
        Map<String, Object> normalUser = new HashMap<>();
        normalUser.put("id", 7L);
        normalUser.put("username", "bob");
        normalUser.put("role", Auths.ROLE_USER);
        ThreadLocalUtil.set(normalUser);

        Auths.requireLogin();   // 不抛即通过
        assertEquals(7L, Auths.currentUserId());
        assertThrows(BusinessException.class, Auths::requireAdmin);
    }

    @Test
    @DisplayName("旧 token 无 role 时按普通用户处理（向下兼容）")
    void missingRoleIsTreatedAsNormalUser() {
        Map<String, Object> legacy = new HashMap<>();
        legacy.put("id", 9L);
        ThreadLocalUtil.set(legacy);

        assertNull(Auths.currentRole());
        assertThrows(BusinessException.class, Auths::requireAdmin);
    }
}
