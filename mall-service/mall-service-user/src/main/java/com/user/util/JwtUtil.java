package com.user.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    /** 密钥最小长度：HMAC256 的强度取决于密钥本身，过短可被暴力枚举 */
    private static final int MIN_SECRET_LENGTH = 32;

    private final String key;

    public JwtUtil(@Value("${jwt.secret}") String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "未配置 JWT 密钥：请设置环境变量 JWT_SECRET（生成示例：openssl rand -base64 32），"
                            + "且必须与 mall-gateway 的取值一致");
        }
        if (key.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT 密钥过短（" + key.length() + " 字符，至少需要 " + MIN_SECRET_LENGTH
                            + "）：HMAC256 强度取决于密钥长度，请用 openssl rand -base64 32 重新生成");
        }
        this.key = key;
    }

    //接收业务数据,生成token并返回
    public String genToken(Map<String, Object> claims) {
        return JWT.create()
                .withClaim("claims", claims)
                .withExpiresAt(new Date(System.currentTimeMillis() + 1000 * 60 * 60 ))
                .sign(Algorithm.HMAC256(key));
    }

    //接收token,验证token,并返回业务数据
    public Map<String, Object> parseToken(String token) {
        return JWT.require(Algorithm.HMAC256(key))
                .build()
                .verify(token)
                .getClaim("claims")
                .asMap();
    }
}
