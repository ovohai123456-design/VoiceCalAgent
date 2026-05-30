package com.voice.agent.controller;

import com.voice.agent.agent.CommandWorkflowService;
import com.voice.agent.model.vo.ExecutionLogVO;
import com.voice.agent.model.vo.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent")
public class WorkflowController {
    private final CommandWorkflowService commandWorkflowService;

    public WorkflowController(CommandWorkflowService commandWorkflowService) {
        this.commandWorkflowService = commandWorkflowService;
    }

    @GetMapping("/tasks/{taskId}/steps")
    public ApiResponse<List<ExecutionLogVO>> listSteps(@PathVariable String taskId) {
        return ApiResponse.ok(commandWorkflowService.listSteps(taskId));
    }
}
