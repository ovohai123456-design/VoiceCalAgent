package com.voice.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class ApiRequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ApiRequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        String path = request.getRequestURI();
        if (request.getQueryString() != null) {
            path = path + "?" + request.getQueryString().replaceAll("(?i)(access_token=)[^&]*", "$1***");
        }

        log.info("HTTP request start method={} path={} remote={}", request.getMethod(), path, request.getRemoteAddr());
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info(
                    "HTTP request end method={} path={} status={} elapsedMs={}",
                    request.getMethod(),
                    path,
                    response.getStatus(),
                    System.currentTimeMillis() - startedAt
            );
        }
    }
}
