package com.voice.agent.service;

import com.voice.agent.mapper.UserPreferenceMapper;
import com.voice.agent.model.entity.UserPreferenceEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserPreferenceService {
    private final UserPreferenceMapper mapper;

    public UserPreferenceService(UserPreferenceMapper mapper) {
        this.mapper = mapper;
    }

    public UserPreferenceEntity get(Long userId) {
        UserPreferenceEntity preference = mapper.selectById(userId);
        if (preference == null) {
            preference = new UserPreferenceEntity();
            preference.setUserId(userId);
        }
        return preference;
    }

    public UserPreferenceEntity save(UserPreferenceEntity preference) {
        if (preference == null || preference.getUserId() == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        UserPreferenceEntity existing = mapper.selectById(preference.getUserId());
        preference.setUpdatedAt(now);
        if (existing == null) {
            preference.setCreatedAt(now);
            mapper.insert(preference);
        } else {
            mapper.updateById(preference);
        }
        return preference;
    }
}
