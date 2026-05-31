package com.voice.agent.controller;

import com.voice.agent.auth.AuthContext;
import com.voice.agent.auth.AuthCookieService;
import com.voice.agent.model.dto.AuthLoginRequest;
import com.voice.agent.model.dto.AuthRegisterRequest;
import com.voice.agent.model.vo.ApiResponse;
import com.voice.agent.model.vo.AuthUserVO;
import com.voice.agent.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthCookieService cookieService;

    public AuthController(AuthService authService, AuthCookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthUserVO> login(@RequestBody AuthLoginRequest request, HttpServletResponse response) {
        return handle(() -> {
            AuthUserVO user = authService.login(request);
            cookieService.addLoginCookie(response, user.getId());
            return user;
        });
    }

    @PostMapping("/register")
    public ApiResponse<AuthUserVO> register(@RequestBody AuthRegisterRequest request, HttpServletResponse response) {
        return handle(() -> {
            AuthUserVO user = authService.register(request);
            cookieService.addLoginCookie(response, user.getId());
            return user;
        });
    }

    @GetMapping("/me")
    public ApiResponse<AuthUserVO> me(HttpServletRequest request) {
        return handle(() -> authService.getCurrentUser(AuthContext.requireUserId(request)));
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(HttpServletResponse response) {
        cookieService.clearLoginCookie(response);
        return ApiResponse.ok(true);
    }

    private <T> ApiResponse<T> handle(Supplier<T> supplier) {
        try {
            return ApiResponse.ok(supplier.get());
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail("AUTH_ERROR", e.getMessage());
        }
    }
}
