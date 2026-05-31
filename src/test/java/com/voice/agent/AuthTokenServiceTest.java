package com.voice.agent;

import com.voice.agent.auth.AuthTokenService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthTokenServiceTest {
    private final AuthTokenService tokenService = new AuthTokenService("test-secret", 24);

    @Test
    void createdTokenShouldResolveUserId() {
        assertEquals(7L, tokenService.parseUserId(tokenService.createToken(7L)));
    }

    @Test
    void tamperedTokenShouldBeRejected() {
        String token = tokenService.createToken(7L);
        assertNull(tokenService.parseUserId(token + "x"));
    }
}
