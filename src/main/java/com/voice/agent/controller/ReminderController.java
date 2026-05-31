package com.voice.agent.controller;

import com.voice.agent.auth.AuthContext;
import com.voice.agent.model.vo.ApiResponse;
import com.voice.agent.model.vo.ReminderJobVO;
import com.voice.agent.service.ReminderJobService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {
    private final ReminderJobService reminderJobService;

    public ReminderController(ReminderJobService reminderJobService) {
        this.reminderJobService = reminderJobService;
    }

    @GetMapping
    public ApiResponse<List<ReminderJobVO>> list(HttpServletRequest request) {
        return ApiResponse.ok(reminderJobService.listByUser(AuthContext.requireUserId(request)));
    }

    @DeleteMapping("/{reminderId}")
    public ApiResponse<Integer> delete(@PathVariable Long reminderId, HttpServletRequest request) {
        return ApiResponse.ok(reminderJobService.deleteById(reminderId, AuthContext.requireUserId(request)));
    }

    @DeleteMapping
    public ApiResponse<Integer> clear(HttpServletRequest request) {
        return ApiResponse.ok(reminderJobService.clearByUser(AuthContext.requireUserId(request)));
    }
}
