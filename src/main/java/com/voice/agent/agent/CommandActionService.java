package com.voice.agent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voice.agent.mapper.CommandActionMapper;
import com.voice.agent.model.entity.CommandActionEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 可执行业务动作服务。
 *
 * <p>动作内容独立于确认记录存在。确认服务只决定动作能否执行，不保存业务 payload。</p>
 */
@Service
public class CommandActionService {
    private final CommandActionMapper commandActionMapper;
    private final ObjectMapper objectMapper;

    public CommandActionService(CommandActionMapper commandActionMapper, ObjectMapper objectMapper) {
        this.commandActionMapper = commandActionMapper;
        this.objectMapper = objectMapper;
    }

    public CommandActionEntity createAction(String taskId, String actionType, Object payload) {
        CommandActionEntity entity = new CommandActionEntity();
        entity.setActionId("action_" + UUID.randomUUID().toString().replace("-", ""));
        entity.setTaskId(taskId);
        entity.setActionType(actionType);
        entity.setPayloadJson(toJson(payload));
        entity.setStatus(AgentConstants.STATUS_PREPARED);
        commandActionMapper.insert(entity);
        return entity;
    }

    public CommandActionEntity getAction(String actionId) {
        CommandActionEntity entity = commandActionMapper.selectById(actionId);
        if (entity == null) {
            throw new IllegalArgumentException("没有找到待执行动作");
        }
        return entity;
    }

    public <T> T readPayload(CommandActionEntity entity, Class<T> payloadType) {
        try {
            return objectMapper.readValue(entity.getPayloadJson(), payloadType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("动作内容解析失败", e);
        }
    }

    public void markWaitingConfirm(CommandActionEntity entity) {
        entity.setStatus(AgentConstants.STATUS_WAITING_CONFIRM);
        commandActionMapper.updateById(entity);
    }

    public void updatePayload(CommandActionEntity entity, Object payload) {
        entity.setPayloadJson(toJson(payload));
        commandActionMapper.updateById(entity);
    }

    public void markExecuted(CommandActionEntity entity) {
        entity.setStatus(AgentConstants.STATUS_EXECUTED);
        entity.setExecutedAt(LocalDateTime.now());
        commandActionMapper.updateById(entity);
    }

    public void markCanceled(CommandActionEntity entity) {
        entity.setStatus(AgentConstants.STATUS_CANCELED);
        commandActionMapper.updateById(entity);
    }

    public void markFailed(CommandActionEntity entity, String errorMessage) {
        entity.setStatus(AgentConstants.STATUS_FAILED);
        entity.setErrorMessage(errorMessage);
        commandActionMapper.updateById(entity);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("动作内容序列化失败", e);
        }
    }
}
