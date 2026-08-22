package com.inventory.intercetors;



import com.inventory.util.JwtUtil;
import com.model.util.ThreadLocalUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;


@Component
public class TokenInterceptor implements HandlerInterceptor {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //令牌验证
        String token = request.getHeader("Authorization");
        try {
            //1.解析token，取出用户id（token过期/伪造会在此抛异常）
            Map<String, Object> claims = jwtUtil.parseToken(token);
            Object idObj = claims.get("id");
            if (idObj == null) {
                throw new RuntimeException("token中缺少用户id");
            }
            Integer id = ((Number) idObj).intValue();
            //2.校验Redis中保存的token是否与当前token一致（支持主动失效和单设备登录）
            String redisToken = stringRedisTemplate.opsForValue().get("login:token:" + id);
            if (redisToken == null || !redisToken.equals(token)) {
                throw new RuntimeException("token已失效");
            }
            //3.把业务数据存储到ThreadLocal中
            ThreadLocalUtil.set(claims);
            //放行
            return true;
        } catch (Exception e) {
            //http响应状态码为401
            response.setStatus(401);
            //不放行
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //清空ThreadLocal中的数据
        ThreadLocalUtil.remove();
    }
}
