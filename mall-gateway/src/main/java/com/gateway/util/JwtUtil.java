package com.gateway.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 网关侧 JWT 解析（只校验签名/过期，不签发）。
 * 密钥由环境变量 JWT_SECRET 注入（与 user 服务一致）；claims 存放于名为 "claims" 的嵌套 claim。
 */
@Component
public class JwtUtil {

    /** 密钥最小长度：HMAC256 的强度取决于密钥本身，过短可被暴力枚举 */
    private static final int MIN_SECRET_LENGTH = 32;

    private final String key;

    public JwtUtil(@Value("${jwt.secret}") String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "未配置 JWT 密钥：请设置环境变量 JWT_SECRET（生成示例：openssl rand -base64 32），"
                            + "且必须与 mall-service-user 的取值一致");
        }
        if (key.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT 密钥过短（" + key.length() + " 字符，至少需要 " + MIN_SECRET_LENGTH
                            + "）：HMAC256 强度取决于密钥长度，请用 openssl rand -base64 32 重新生成");
        }
        this.key = key;
    }

    public Map<String, Object> parseToken(String token) {
        return JWT.require(Algorithm.HMAC256(key))
                .build()
                .verify(token)
                .getClaim("claims")
                .asMap();
    }
}
