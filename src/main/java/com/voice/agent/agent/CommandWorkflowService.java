package com.voice.agent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voice.agent.mapper.CommandTaskMapper;
import com.voice.agent.mapper.ExecutionLogMapper;
import com.voice.agent.model.dto.AgentExecuteRequest;
import com.voice.agent.model.entity.CommandTaskEntity;
import com.voice.agent.model.entity.ExecutionLogEntity;
import com.voice.agent.model.vo.ExecutionLogVO;
import com.voice.agent.stream.AgentEventStreamService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户命令任务与执行日志服务。
 *
 * <p>command_task 保存任务最终状态，execution_log 只记录过程并用于前端时间线展示。</p>
 */
@Service
public class CommandWorkflowService {
    private final CommandTaskMapper commandTaskMapper;
    private final ExecutionLogMapper executionLogMapper;
    private final ExecutionLogWriter executionLogWriter;
    private final ObjectMapper objectMapper;
    private final AgentEventStreamService eventStreamService;

    public CommandWorkflowService(
            CommandTaskMapper commandTaskMapper,
            ExecutionLogMapper executionLogMapper,
            ExecutionLogWriter executionLogWriter,
            ObjectMapper objectMapper,
            AgentEventStreamService eventStreamService
    ) {
        this.commandTaskMapper = commandTaskMapper;
        this.executionLogMapper = executionLogMapper;
        this.executionLogWriter = executionLogWriter;
        this.objectMapper = objectMapper;
        this.eventStreamService = eventStreamService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CommandTaskEntity createTask(AgentExecuteRequest request) {
        CommandTaskEntity task = new CommandTaskEntity();
        task.setTaskId("task_" + UUID.randomUUID().toString().replace("-", ""));
        task.setUserId(request.getUserId());
        task.setSessionId(StringUtils.hasText(request.getSessionId()) ? request.getSessionId() : "default_session");
        task.setInputType(StringUtils.hasText(request.getInputType()) ? request.getInputType() : "TEXT");
        task.setInputText(request.getText());
        task.setStatus(AgentConstants.STATUS_RUNNING);
        task.setStartedAt(LocalDateTime.now());
        commandTaskMapper.insert(task);
        registerTaskAfterCommit(task);
        return task;
    }

    public ExecutionLogEntity addLog(String taskId, Integer stepOrder, String skillId, String stepName, String executorType, Object request) {
        ExecutionLogEntity step = new ExecutionLogEntity();
        step.setTaskId(taskId);
        step.setStepOrder(stepOrder);
        step.setSkillId(skillId);
        step.setStepName(stepName);
        step.setExecutorType(executorType);
        step.setRequestJson(toJson(request));
        step.setStatus(AgentConstants.STATUS_RUNNING);
        step.setStartedAt(LocalDateTime.now());
        eventStreamService.publishWorkflowStep(taskId, toStepVO(step));
        return step;
    }

    public void markLogSuccess(ExecutionLogEntity step, Object response) {
        step.setStatus(AgentConstants.STATUS_SUCCESS);
        step.setResponseJson(toJson(response));
        step.setFinishedAt(LocalDateTime.now());
        step.setLatencyMs(resolveLatency(step));
        eventStreamService.publishWorkflowStep(step.getTaskId(), toStepVO(step));
        persistAfterCommit(step);
    }

    public void markLogFailed(ExecutionLogEntity step, String errorMessage) {
        step.setStatus(AgentConstants.STATUS_FAILED);
        step.setErrorMessage(errorMessage);
        step.setFinishedAt(LocalDateTime.now());
        step.setLatencyMs(resolveLatency(step));
        eventStreamService.publishWorkflowStep(step.getTaskId(), toStepVO(step));
        persistAfterCommit(step);
    }

    public void markWaitingConfirm(String taskId, String intent, String replyText, String speakText) {
        CommandTaskEntity task = new CommandTaskEntity();
        task.setTaskId(taskId);
        task.setIntent(intent);
        task.setStatus(AgentConstants.STATUS_WAITING_CONFIRM);
        task.setReplyText(replyText);
        task.setSpeakText(speakText);
        commandTaskMapper.updateById(task);
        eventStreamService.publishTaskStatus(taskId, AgentConstants.STATUS_WAITING_CONFIRM);
    }

    public void markNeedClarification(String taskId, String intent, String replyText) {
        CommandTaskEntity task = new CommandTaskEntity();
        task.setTaskId(taskId);
        task.setIntent(intent);
        task.setStatus(AgentConstants.STATUS_NEED_CLARIFICATION);
        task.setReplyText(replyText);
        task.setSpeakText(replyText);
        commandTaskMapper.updateById(task);
        eventStreamService.publishTaskStatus(taskId, AgentConstants.STATUS_NEED_CLARIFICATION);
    }

    public void finishSuccess(String taskId, String intent, String replyText, String speakText) {
        CommandTaskEntity task = new CommandTaskEntity();
        task.setTaskId(taskId);
        task.setIntent(intent);
        task.setStatus(AgentConstants.STATUS_SUCCESS);
        task.setReplyText(replyText);
        task.setSpeakText(speakText);
        task.setFinishedAt(LocalDateTime.now());
        commandTaskMapper.updateById(task);
        eventStreamService.publishTaskStatus(taskId, AgentConstants.STATUS_SUCCESS);
    }

    public void finishFailed(String taskId, String intent, String errorMessage, String replyText) {
        CommandTaskEntity task = new CommandTaskEntity();
        task.setTaskId(taskId);
        task.setIntent(intent);
        task.setStatus(AgentConstants.STATUS_FAILED);
        task.setErrorMessage(errorMessage);
        task.setReplyText(replyText);
        task.setSpeakText(replyText);
        task.setFinishedAt(LocalDateTime.now());
        commandTaskMapper.updateById(task);
        eventStreamService.publishTaskStatus(taskId, AgentConstants.STATUS_FAILED);
    }

    public void cancelTask(String taskId, String replyText) {
        CommandTaskEntity task = new CommandTaskEntity();
        task.setTaskId(taskId);
        task.setStatus(AgentConstants.STATUS_CANCELED);
        task.setReplyText(replyText);
        task.setSpeakText(replyText);
        task.setFinishedAt(LocalDateTime.now());
        commandTaskMapper.updateById(task);
        eventStreamService.publishTaskStatus(taskId, AgentConstants.STATUS_CANCELED);
    }

    public CommandTaskEntity findTask(String taskId) {
        return commandTaskMapper.selectById(taskId);
    }

    public CommandTaskEntity findLatestNeedClarification(Long userId, String sessionId, Integer timeoutMinutes) {
        return commandTaskMapper.selectOne(
                new LambdaQueryWrapper<CommandTaskEntity>()
                        .eq(CommandTaskEntity::getUserId, userId)
                        .eq(CommandTaskEntity::getSessionId, sessionId)
                        .eq(CommandTaskEntity::getStatus, AgentConstants.STATUS_NEED_CLARIFICATION)
                        .ge(CommandTaskEntity::getCreatedAt, LocalDateTime.now().minusMinutes(timeoutMinutes))
                        .orderByDesc(CommandTaskEntity::getCreatedAt)
                        .last("LIMIT 1")
        );
    }

    public void markClarificationContinued(String taskId) {
        CommandTaskEntity task = new CommandTaskEntity();
        task.setTaskId(taskId);
        task.setStatus(AgentConstants.STATUS_CANCELED);
        task.setReplyText("已通过后续输入补充信息。");
        task.setSpeakText("已通过后续输入补充信息。");
        task.setFinishedAt(LocalDateTime.now());
        commandTaskMapper.updateById(task);
        eventStreamService.publishTaskStatus(taskId, AgentConstants.STATUS_CANCELED);
    }

    public List<ExecutionLogVO> listSteps(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            throw new IllegalArgumentException("taskId不能为空");
        }

        return executionLogMapper.selectList(
                new LambdaQueryWrapper<ExecutionLogEntity>()
                        .eq(ExecutionLogEntity::getTaskId, taskId)
                        .orderByAsc(ExecutionLogEntity::getStepOrder)
                        .orderByAsc(ExecutionLogEntity::getId)
        ).stream().map(this::toStepVO).collect(Collectors.toList());
    }

    public List<ExecutionLogVO> listSteps(String taskId, Long userId) {
        CommandTaskEntity task = findTask(taskId);
        if (task == null || userId == null || !userId.equals(task.getUserId())) {
            throw new IllegalArgumentException("任务不存在");
        }
        return listSteps(taskId);
    }

    private ExecutionLogVO toStepVO(ExecutionLogEntity entity) {
        ExecutionLogVO vo = new ExecutionLogVO();
        vo.setId(entity.getId());
        vo.setTaskId(entity.getTaskId());
        vo.setStepOrder(entity.getStepOrder());
        vo.setSkillId(entity.getSkillId());
        vo.setStepName(entity.getStepName());
        vo.setExecutorType(entity.getExecutorType());
        vo.setStatus(entity.getStatus());
        vo.setLatencyMs(entity.getLatencyMs());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setStartedAt(entity.getStartedAt());
        vo.setFinishedAt(entity.getFinishedAt());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private Long resolveLatency(ExecutionLogEntity step) {
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

    private void persistAfterCommit(ExecutionLogEntity step) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            executionLogWriter.write(step);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executionLogWriter.write(step);
            }
        });
    }

    private void registerTaskAfterCommit(CommandTaskEntity task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventStreamService.registerTask(task);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventStreamService.registerTask(task);
            }
        });
    }
}
