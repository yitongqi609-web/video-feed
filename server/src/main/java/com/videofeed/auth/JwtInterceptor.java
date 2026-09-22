package com.videofeed.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videofeed.common.ApiResponse;
import com.videofeed.common.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import io.jsonwebtoken.Claims;

import java.nio.charset.StandardCharsets;

/**
 * 统一鉴权拦截器：校验 Bearer Access Token + 登出黑名单
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    public static final String BLACKLIST_PREFIX = "auth:blk:";

    private final JwtService jwtService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return reject(response, "未登录");
        }
        Claims claims;
        try {
            claims = jwtService.parse(auth.substring(7));
        } catch (BizException e) {
            return reject(response, e.getMessage());
        }
        if (!JwtService.TYPE_ACCESS.equals(claims.get("type", String.class))) {
            return reject(response, "请使用 Access Token 访问");
        }
        // 登出黑名单：jti 还在有效期内但已被注销
        if (Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + claims.getId()))) {
            return reject(response, "令牌已注销，请重新登录");
        }
        UserContext.set(JwtService.uidOf(claims), claims.getSubject());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean reject(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(401, message)));
        return false;
    }
}
