package com.mall.common.web;

import com.model.exception.BusinessException;
import com.model.util.ThreadLocalUtil;

import java.util.Map;

/**
 * 身份/角色断言工具（读 ThreadLocal，由 gateway 注入 X-User-* 头、下游拦截器写入；
 * user 服务则由本地 LoginInterceptor 从 JWT claims 写入，语义一致）。
 * 业务服务直接调用本类静态方法即可，无需 @Import / 组件扫描。
 */
public final class Auths {

    public static final int ROLE_USER = 1;   // 普通用户（注册默认）
    public static final int ROLE_ADMIN = 2;  // 管理员（内部系统）

    private Auths() {
    }

    public static Long currentUserId() {
        Map<String, Object> map = ThreadLocalUtil.get();
        if (map == null || map.get("id") == null) {
            return null;
        }
        Object v = map.get("id");
        return v instanceof Number n ? n.longValue() : null;
    }

    public static Integer currentRole() {
        Map<String, Object> map = ThreadLocalUtil.get();
        if (map == null) {
            return null;
        }
        Object v = map.get("role");
        if (v == null) {
            return null;
        }
        return v instanceof Number n ? n.intValue() : null;
    }

    public static void requireLogin() {
        if (currentUserId() == null) {
            throw new BusinessException("请先登录");
        }
    }

    public static void requireAdmin() {
        if (!Integer.valueOf(ROLE_ADMIN).equals(currentRole())) {
            throw new BusinessException("无权限，仅管理员可操作");
        }
    }
}
