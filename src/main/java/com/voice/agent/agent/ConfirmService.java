package com.voice.agent.agent;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.voice.agent.mapper.PendingConfirmationMapper;
import com.voice.agent.model.entity.PendingConfirmationEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 待确认记录服务。
 *
 * <p>创建、修改、删除这类高风险动作先写 command_action，再生成 pending_confirmation 等待用户确认。</p>
 */
@Service
public class ConfirmService {
    private final PendingConfirmationMapper pendingConfirmationMapper;

    @Value("${voicecal.confirm.expire-minutes:5}")
    private Integer expireMinutes;

    public ConfirmService(PendingConfirmationMapper pendingConfirmationMapper) {
        this.pendingConfirmationMapper = pendingConfirmationMapper;
    }

    public PendingConfirmationEntity createPendingConfirmation(Long userId, String sessionId, String actionId) {
        PendingConfirmationEntity entity = new PendingConfirmationEntity();
        entity.setActionId(actionId);
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setConfirmToken("ct_" + UUID.randomUUID().toString().replace("-", ""));
        entity.setStatus(AgentConstants.STATUS_PENDING);
        entity.setExpireAt(LocalDateTime.now().plusMinutes(expireMinutes));
        pendingConfirmationMapper.insert(entity);
        return entity;
    }

    public PendingConfirmationEntity getPendingConfirmation(Long userId, String sessionId, String confirmToken) {
        PendingConfirmationEntity entity = pendingConfirmationMapper.selectOne(Wrappers.lambdaQuery(PendingConfirmationEntity.class)
                .eq(PendingConfirmationEntity::getUserId, userId)
                .eq(PendingConfirmationEntity::getSessionId, sessionId)
                .eq(PendingConfirmationEntity::getConfirmToken, confirmToken)
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

    public void markConfirmed(PendingConfirmationEntity entity) {
        entity.setStatus(AgentConstants.STATUS_CONFIRMED);
        entity.setConfirmedAt(LocalDateTime.now());
        pendingConfirmationMapper.updateById(entity);
    }

    public void markCanceled(PendingConfirmationEntity entity) {
        entity.setStatus(AgentConstants.STATUS_CANCELED);
        entity.setCanceledAt(LocalDateTime.now());
        pendingConfirmationMapper.updateById(entity);
    }

    private void markExpired(PendingConfirmationEntity entity) {
        entity.setStatus(AgentConstants.STATUS_EXPIRED);
        pendingConfirmationMapper.updateById(entity);
    }
}
