package com.voice.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voice.agent.auth.AuthContext;
import com.voice.agent.auth.AuthCookieService;
import com.voice.agent.auth.AuthTokenFilter;
import com.voice.agent.auth.AuthTokenService;
import com.voice.agent.mapper.UserMapper;
import com.voice.agent.model.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthTokenFilterTest {
    private final AuthTokenService tokenService = new AuthTokenService("test-secret", 24);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final AuthTokenFilter filter = new AuthTokenFilter(tokenService, new ObjectMapper(), userMapper);

    @Test
    void protectedApiShouldAcceptLoginCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/calendar/events");
        request.setCookies(new Cookie(AuthCookieService.COOKIE_NAME, tokenService.createToken(9L)));
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserEntity user = new UserEntity();
        user.setId(9L);
        user.setStatus("ACTIVE");
        when(userMapper.selectById(9L)).thenReturn(user);

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(9L, request.getAttribute(AuthContext.USER_ID_ATTRIBUTE));
    }

    @Test
    void protectedApiShouldRejectAnonymousRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/calendar/events");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }
}
