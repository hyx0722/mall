package com.gateway.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 网关侧 JWT 解析（只校验签名/过期，不签发）。
 * 密钥与 user 服务一致；claims 存放于名为 "claims" 的嵌套 claim。
 */
@Component
public class JwtUtil {
    private final String key;

    public JwtUtil(@Value("${jwt.secret}") String key) {
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
