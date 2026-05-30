package com.voice.agent.controller;

import com.voice.agent.model.vo.ApiResponse;
import com.voice.agent.model.vo.ReminderJobVO;
import com.voice.agent.service.ReminderJobService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {
    private final ReminderJobService reminderJobService;

    public ReminderController(ReminderJobService reminderJobService) {
        this.reminderJobService = reminderJobService;
    }

    @GetMapping
    public ApiResponse<List<ReminderJobVO>> list(@RequestParam Long userId) {
        return ApiResponse.ok(reminderJobService.listByUser(userId));
    }
}
