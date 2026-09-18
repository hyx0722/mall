package com.model.constant;

/**
 * 跨服务的 Redis 键契约。
 *
 * 放在 model（而非某个服务内）是因为这些键**必须由两端逐字一致**：
 * 登录态键由 mall-service-user 写入/删除，却由 mall-gateway 读取校验。
 * 此前该字面量散落在 6 处硬编码，"改一边忘另一边"会导致登录成功但立刻 401
 * 且没有任何编译期信号——正是需要收敛成常量的那类耦合。
 */
public final class RedisKeys {

    /** 登录态：login:token:{userId} -> JWT 原文（用于单设备登录与主动失效） */
    private static final String LOGIN_TOKEN_PREFIX = "login:token:";

    private RedisKeys() {
    }

    public static String loginToken(Long userId) {
        return LOGIN_TOKEN_PREFIX + userId;
    }
}
