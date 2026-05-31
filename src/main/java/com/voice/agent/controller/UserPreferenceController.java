package com.voice.agent.controller;

import com.voice.agent.auth.AuthContext;
import com.voice.agent.model.entity.UserPreferenceEntity;
import com.voice.agent.model.vo.ApiResponse;
import com.voice.agent.service.UserPreferenceService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
@RestController
@RequestMapping("/api/preferences")
public class UserPreferenceController {
    private final UserPreferenceService service;

    public UserPreferenceController(UserPreferenceService service) { this.service = service; }

    @GetMapping
    public ApiResponse<UserPreferenceEntity> get(HttpServletRequest request) {
        return ApiResponse.ok(service.get(AuthContext.requireUserId(request)));
    }

    @PutMapping
    public ApiResponse<UserPreferenceEntity> save(@RequestBody UserPreferenceEntity preference, HttpServletRequest request) {
        preference.setUserId(AuthContext.requireUserId(request));
        return ApiResponse.ok(service.save(preference));
    }
}
