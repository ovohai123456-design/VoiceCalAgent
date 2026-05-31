package com.voice.agent.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.time.Duration;

@Service
public class AuthCookieService {
    public static final String COOKIE_NAME = "voicecal_token";

    private final AuthTokenService tokenService;
    private final boolean secure;

    public AuthCookieService(
            AuthTokenService tokenService,
            @Value("${voicecal.auth.cookie-secure:false}") boolean secure
    ) {
        this.tokenService = tokenService;
        this.secure = secure;
    }

    public void addLoginCookie(HttpServletResponse response, Long userId) {
        addCookie(response, tokenService.createToken(userId), Duration.ofSeconds(tokenService.getExpireSeconds()));
    }

    public void clearLoginCookie(HttpServletResponse response) {
        addCookie(response, "", Duration.ZERO);
    }

    private void addCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
