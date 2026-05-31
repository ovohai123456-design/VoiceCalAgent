package com.voice.agent.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voice.agent.mapper.UserMapper;
import com.voice.agent.model.entity.UserEntity;
import com.voice.agent.model.vo.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class AuthTokenFilter extends OncePerRequestFilter {
    private final AuthTokenService tokenService;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;

    public AuthTokenFilter(AuthTokenService tokenService, ObjectMapper objectMapper, UserMapper userMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.userMapper = userMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || "/api/auth/login".equals(path)
                || "/api/auth/register".equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Long userId = tokenService.parseUserId(resolveToken(request));
        UserEntity user = userId == null ? null : userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiResponse.fail("UNAUTHORIZED", "请先登录"));
            return;
        }
        request.setAttribute(AuthContext.USER_ID_ATTRIBUTE, userId);
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthCookieService.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
