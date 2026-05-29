package com.voice.agent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voice.agent.mapper.AgentStepMapper;
import com.voice.agent.mapper.AgentTaskMapper;
import com.voice.agent.model.dto.AgentExecuteRequest;
import com.voice.agent.model.entity.AgentStepEntity;
import com.voice.agent.model.entity.AgentTaskEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Workflow 记录服务。
 *
 * <p>负责写 agent_task 和 agent_step，业务 Agent 只要报告关键阶段即可。</p>
 */
@Service
public class WorkflowService {
    private final AgentTaskMapper agentTaskMapper;
    private final AgentStepMapper agentStepMapper;
    private final ObjectMapper objectMapper;

    public WorkflowService(AgentTaskMapper agentTaskMapper, AgentStepMapper agentStepMapper, ObjectMapper objectMapper) {
        this.agentTaskMapper = agentTaskMapper;
        this.agentStepMapper = agentStepMapper;
        this.objectMapper = objectMapper;
    }

    public AgentTaskEntity createTask(AgentExecuteRequest request) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setTaskId("task_" + UUID.randomUUID().toString().replace("-", ""));
        task.setUserId(request.getUserId());
        task.setSessionId(StringUtils.hasText(request.getSessionId()) ? request.getSessionId() : "default_session");
        task.setInputType(StringUtils.hasText(request.getInputType()) ? request.getInputType() : "TEXT");
        task.setRequestText(request.getText());
        task.setStatus(AgentConstants.STATUS_RUNNING);
        task.setNeedConfirm(false);
        task.setStartedAt(LocalDateTime.now());
        agentTaskMapper.insert(task);
        return task;
    }

    public AgentStepEntity addStep(String taskId, Integer stepOrder, String skillId, String stepName, String executorType, Object request) {
        AgentStepEntity step = new AgentStepEntity();
        step.setTaskId(taskId);
        step.setStepOrder(stepOrder);
        step.setSkillId(skillId);
        step.setStepName(stepName);
        step.setExecutorType(executorType);
        step.setRequestJson(toJson(request));
        step.setStatus(AgentConstants.STATUS_RUNNING);
        step.setStartedAt(LocalDateTime.now());
        agentStepMapper.insert(step);
        return step;
    }

    public void markStepSuccess(AgentStepEntity step, Object response) {
        step.setStatus(AgentConstants.STATUS_SUCCESS);
        step.setResponseJson(toJson(response));
        step.setFinishedAt(LocalDateTime.now());
        step.setLatencyMs(resolveLatency(step));
        agentStepMapper.updateById(step);
    }

    public void markStepFailed(AgentStepEntity step, String errorMessage) {
        step.setStatus(AgentConstants.STATUS_FAILED);
        step.setErrorMessage(errorMessage);
        step.setFinishedAt(LocalDateTime.now());
        step.setLatencyMs(resolveLatency(step));
        agentStepMapper.updateById(step);
    }

    public void markWaitingConfirm(String taskId, String intent, String confirmToken, String replyText, String speakText) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setTaskId(taskId);
        task.setIntent(intent);
        task.setStatus(AgentConstants.STATUS_WAITING_CONFIRM);
        task.setNeedConfirm(true);
        task.setConfirmToken(confirmToken);
        task.setReplyText(replyText);
        task.setSpeakText(speakText);
        agentTaskMapper.updateById(task);
    }

    public void finishSuccess(String taskId, String intent, String replyText, String speakText) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setTaskId(taskId);
        task.setIntent(intent);
        task.setStatus(AgentConstants.STATUS_SUCCESS);
        task.setNeedConfirm(false);
        task.setReplyText(replyText);
        task.setSpeakText(speakText);
        task.setFinishedAt(LocalDateTime.now());
        agentTaskMapper.updateById(task);
    }

    public void finishFailed(String taskId, String intent, String errorMessage, String replyText) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setTaskId(taskId);
        task.setIntent(intent);
        task.setStatus(AgentConstants.STATUS_FAILED);
        task.setNeedConfirm(false);
        task.setErrorMessage(errorMessage);
        task.setReplyText(replyText);
        task.setSpeakText(replyText);
        task.setFinishedAt(LocalDateTime.now());
        agentTaskMapper.updateById(task);
    }

    public void cancelTask(String taskId, String replyText) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.setTaskId(taskId);
        task.setStatus(AgentConstants.STATUS_CANCELED);
        task.setNeedConfirm(false);
        task.setReplyText(replyText);
        task.setSpeakText(replyText);
        task.setFinishedAt(LocalDateTime.now());
        agentTaskMapper.updateById(task);
    }

    public AgentTaskEntity findTask(String taskId) {
        return agentTaskMapper.selectById(taskId);
    }

    private Long resolveLatency(AgentStepEntity step) {
        if (step.getStartedAt() == null || step.getFinishedAt() == null) {
            return null;
        }
        return Duration.between(step.getStartedAt(), step.getFinishedAt()).toMillis();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"json_serialize_failed\"}";
        }
    }
}
