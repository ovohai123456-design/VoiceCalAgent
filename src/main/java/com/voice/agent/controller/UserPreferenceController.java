package com.voice.agent.controller;

import com.voice.agent.model.entity.UserPreferenceEntity;
import com.voice.agent.model.vo.ApiResponse;
import com.voice.agent.service.UserPreferenceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/preferences")
public class UserPreferenceController {
    private final UserPreferenceService service;

    public UserPreferenceController(UserPreferenceService service) { this.service = service; }

    @GetMapping
    public ApiResponse<UserPreferenceEntity> get(@RequestParam Long userId) {
        return ApiResponse.ok(service.get(userId));
    }

    @PutMapping
    public ApiResponse<UserPreferenceEntity> save(@RequestBody UserPreferenceEntity preference) {
        return ApiResponse.ok(service.save(preference));
    }
}
