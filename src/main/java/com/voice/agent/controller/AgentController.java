package com.voice.agent.controller;

import com.voice.agent.agent.AgentApplicationService;
import com.voice.agent.model.dto.AgentCancelRequest;
import com.voice.agent.model.dto.AgentConfirmRequest;
import com.voice.agent.model.dto.AgentExecuteRequest;
import com.voice.agent.model.vo.AgentResponse;
import com.voice.agent.model.vo.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

/**
 * Agent 入口接口。
 *
 * <p>RouterAgent 会先判断任务类型，再把任务交给具体领域 Agent。第一版创建类任务需要二次确认。</p>
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentApplicationService agentApplicationService;

    public AgentController(AgentApplicationService agentApplicationService) {
        this.agentApplicationService = agentApplicationService;
    }

    @PostMapping("/execute")
    public ApiResponse<AgentResponse> execute(@RequestBody AgentExecuteRequest request) {
        return handle(() -> agentApplicationService.execute(request));
    }

    @PostMapping("/confirm")
    public ApiResponse<AgentResponse> confirm(@RequestBody AgentConfirmRequest request) {
        return handle(() -> agentApplicationService.confirm(request));
    }

    @PostMapping("/cancel")
    public ApiResponse<AgentResponse> cancel(@RequestBody AgentCancelRequest request) {
        return handle(() -> agentApplicationService.cancel(request));
    }

    private ApiResponse<AgentResponse> handle(Supplier<AgentResponse> supplier) {
        try {
            return ApiResponse.ok(supplier.get());
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail("BAD_REQUEST", e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail("AGENT_STATE_ERROR", e.getMessage());
        }
    }
}
