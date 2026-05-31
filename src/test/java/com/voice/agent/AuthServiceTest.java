package com.voice.agent;

import com.voice.agent.mapper.UserMapper;
import com.voice.agent.model.dto.AuthLoginRequest;
import com.voice.agent.model.dto.AuthRegisterRequest;
import com.voice.agent.model.entity.UserEntity;
import com.voice.agent.model.vo.AuthUserVO;
import com.voice.agent.service.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private final UserMapper userMapper = mock(UserMapper.class);
    private final AuthService authService = new AuthService(userMapper);

    @Test
    void registerShouldHashPassword() {
        when(userMapper.insert(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(7L);
            return 1;
        });

        AuthRegisterRequest request = new AuthRegisterRequest();
        request.setUsername("alice");
        request.setPassword("secret123");
        request.setDisplayName("Alice");
        AuthUserVO result = authService.register(request);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(captor.capture());
        assertEquals(7L, result.getId());
        assertEquals("Alice", result.getDisplayName());
        assertTrue(new BCryptPasswordEncoder().matches("secret123", captor.getValue().getPasswordHash()));
    }

    @Test
    void loginShouldVerifyPassword() {
        UserEntity user = new UserEntity();
        user.setId(8L);
        user.setUsername("bob");
        user.setPasswordHash(new BCryptPasswordEncoder().encode("secret456"));
        user.setDisplayName("Bob");
        user.setStatus("ACTIVE");
        when(userMapper.selectOne(any())).thenReturn(user);

        AuthLoginRequest request = new AuthLoginRequest();
        request.setUsername("bob");
        request.setPassword("secret456");

        assertEquals(8L, authService.login(request).getId());
    }
}
