package com.voice.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.voice.agent.mapper.UserMapper;
import com.voice.agent.model.dto.AuthLoginRequest;
import com.voice.agent.model.dto.AuthRegisterRequest;
import com.voice.agent.model.entity.UserEntity;
import com.voice.agent.model.vo.AuthUserVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{3,64}");
    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public AuthUserVO login(AuthLoginRequest request) {
        String username = normalizeUsername(request == null ? null : request.getUsername());
        String password = request == null ? null : request.getPassword();
        UserEntity user = findByUsername(username);
        if (user == null || !StringUtils.hasText(user.getPasswordHash()) || !passwordMatches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalArgumentException("该用户已停用");
        }
        return toVO(user);
    }

    @Transactional
    public AuthUserVO register(AuthRegisterRequest request) {
        String username = normalizeUsername(request == null ? null : request.getUsername());
        String password = request == null ? null : request.getPassword();
        validateUsername(username);
        validatePassword(password);
        if (findByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(resolveDisplayName(request.getDisplayName(), username));
        user.setTimezone("Asia/Shanghai");
        user.setStatus("ACTIVE");
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("用户名已存在");
        }
        return toVO(user);
    }

    public AuthUserVO getCurrentUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new IllegalArgumentException("用户不存在或已停用");
        }
        return toVO(user);
    }

    private UserEntity findByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return userMapper.selectOne(
                Wrappers.lambdaQuery(UserEntity.class)
                        .eq(UserEntity::getUsername, username)
                        .last("LIMIT 1")
        );
    }

    private boolean passwordMatches(String password, String passwordHash) {
        return password != null && passwordEncoder.matches(password, passwordHash);
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim();
    }

    private void validateUsername(String username) {
        if (!StringUtils.hasText(username) || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("用户名只能包含字母、数字、下划线或短横线，长度为 3 到 64 位");
        }
    }

    private void validatePassword(String password) {
        if (!StringUtils.hasText(password) || password.length() < 6
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            throw new IllegalArgumentException("密码长度至少为 6 位，且不能超过 72 个 UTF-8 字节");
        }
    }

    private String resolveDisplayName(String displayName, String username) {
        if (!StringUtils.hasText(displayName)) {
            return username;
        }
        String normalized = displayName.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("显示名称不能超过 100 个字符");
        }
        return normalized;
    }

    private AuthUserVO toVO(UserEntity user) {
        AuthUserVO result = new AuthUserVO();
        result.setId(user.getId());
        result.setUsername(user.getUsername());
        result.setDisplayName(user.getDisplayName());
        result.setTimezone(user.getTimezone());
        return result;
    }
}
