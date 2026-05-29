package com.voice.agent.agent;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voice.agent.mapper.PendingActionMapper;
import com.voice.agent.model.entity.PendingActionEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 待确认操作服务。
 *
 * <p>创建、修改、删除这类高风险动作先写 pending_action，用户确认后再执行真实工具。</p>
 */
@Service
public class ConfirmService {
    private final PendingActionMapper pendingActionMapper;
    private final ObjectMapper objectMapper;

    @Value("${voicecal.confirm.expire-minutes:5}")
    private Integer expireMinutes;

    public ConfirmService(PendingActionMapper pendingActionMapper, ObjectMapper objectMapper) {
        this.pendingActionMapper = pendingActionMapper;
        this.objectMapper = objectMapper;
    }

    public PendingActionEntity createPendingAction(Long userId, String sessionId, String taskId, String actionType, Object payload) {
        PendingActionEntity entity = new PendingActionEntity();
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setTaskId(taskId);
        entity.setConfirmToken("ct_" + UUID.randomUUID().toString().replace("-", ""));
        entity.setActionType(actionType);
        entity.setPayloadJson(toJson(payload));
        entity.setStatus(AgentConstants.STATUS_PENDING);
        entity.setExpireAt(LocalDateTime.now().plusMinutes(expireMinutes));
        pendingActionMapper.insert(entity);
        return entity;
    }

    public PendingActionEntity getPendingAction(Long userId, String sessionId, String confirmToken) {
        PendingActionEntity entity = pendingActionMapper.selectOne(Wrappers.lambdaQuery(PendingActionEntity.class)
                .eq(PendingActionEntity::getUserId, userId)
                .eq(PendingActionEntity::getSessionId, sessionId)
                .eq(PendingActionEntity::getConfirmToken, confirmToken)
                .last("LIMIT 1"));
        if (entity == null) {
            throw new IllegalArgumentException("没有找到待确认操作");
        }
        if (!AgentConstants.STATUS_PENDING.equals(entity.getStatus())) {
            throw new IllegalStateException("该操作已经处理过");
        }
        if (entity.getExpireAt() != null && entity.getExpireAt().isBefore(LocalDateTime.now())) {
            markExpired(entity);
            throw new IllegalStateException("确认已过期，请重新发起操作");
        }
        return entity;
    }

    public <T> T readPayload(PendingActionEntity entity, Class<T> payloadType) {
        try {
            return objectMapper.readValue(entity.getPayloadJson(), payloadType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("待确认操作内容解析失败", e);
        }
    }

    public void markExecuted(PendingActionEntity entity) {
        entity.setStatus(AgentConstants.STATUS_EXECUTED);
        entity.setExecutedAt(LocalDateTime.now());
        pendingActionMapper.updateById(entity);
    }

    public void markCanceled(PendingActionEntity entity) {
        entity.setStatus(AgentConstants.STATUS_CANCELED);
        pendingActionMapper.updateById(entity);
    }

    private void markExpired(PendingActionEntity entity) {
        entity.setStatus(AgentConstants.STATUS_EXPIRED);
        pendingActionMapper.updateById(entity);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("待确认操作内容序列化失败", e);
        }
    }
}
