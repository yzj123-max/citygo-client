package com.demo.logindemo.interceptor;

import com.demo.logindemo.entity.Result;
import com.demo.logindemo.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        // 放行：注册、账号登录、验证码获取、验证码登录、异常error页面
        if (uri.contains("/userLogin") || uri.contains("/userRegister") || uri.contains("/sendCode") || uri.contains("/userLogin/code") || uri.contains("/error")) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token == null || token.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write(OBJECT_MAPPER.writeValueAsString(Result.unauthorized("未登录，请携带token")));
            return false;
        }
        if(token.startsWith("Bearer ")){
            token = token.substring(7);
        }
        try {
            Claims claims = JwtUtil.parseToken(token);
            Long userId = Long.valueOf(claims.getSubject());
            request.setAttribute("userId", userId);
        } catch (Exception e) {
            // 仅记录诊断元信息，不打印 Token、密钥或异常消息中的签名内容。
            org.slf4j.LoggerFactory.getLogger(JwtInterceptor.class).warn(
                    "JWT校验失败：path={}, type={}, authorizationCount={}, tokenLength={}, parts={}, hasWhitespace={}, hasQuotes={}",
                    uri,
                    e.getClass().getSimpleName(),
                    java.util.Collections.list(request.getHeaders("Authorization")).size(),
                    token.length(),
                    token.split("\\.", -1).length,
                    token.chars().anyMatch(Character::isWhitespace),
                    token.indexOf('"') >= 0
            );
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write(OBJECT_MAPPER.writeValueAsString(Result.unauthorized("token无效或已过期")));
            return false;
        }
        return true;
    }
}
