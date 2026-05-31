package com.voice.agent.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voice.agent.mapper.ConversationMessageMapper;
import com.voice.agent.mapper.ConversationStateMapper;
import com.voice.agent.mapper.ConversationSessionContextMapper;
import com.voice.agent.model.entity.ConversationMessageEntity;
import com.voice.agent.model.entity.ConversationSessionContextEntity;
import com.voice.agent.model.entity.ConversationStateEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class ConversationMemoryService {
    private final ConversationMessageMapper messageMapper;
    private final ConversationStateMapper stateMapper;
    private final ConversationSessionContextMapper sessionContextMapper;
    private final ObjectMapper objectMapper;

    public ConversationMemoryService(
            ConversationMessageMapper messageMapper,
            ConversationStateMapper stateMapper,
            ConversationSessionContextMapper sessionContextMapper,
            ObjectMapper objectMapper
    ) {
        this.messageMapper = messageMapper;
        this.stateMapper = stateMapper;
        this.sessionContextMapper = sessionContextMapper;
        this.objectMapper = objectMapper;
    }

    public void saveUserMessage(Long userId, String sessionId, String content, String taskId) {
        saveMessage(userId, sessionId, ConversationConstants.ROLE_USER, content, taskId);
    }

    public void saveAssistantMessage(Long userId, String sessionId, String content, String taskId) {
        saveMessage(userId, sessionId, ConversationConstants.ROLE_ASSISTANT, content, taskId);
    }

    private void saveMessage(Long userId, String sessionId, String role, String content, String taskId) {
        if (userId == null || !StringUtils.hasText(sessionId) || !StringUtils.hasText(content)) {
            return;
        }
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setRole(role);
        entity.setContent(content);
        entity.setTaskId(taskId);
        messageMapper.insert(entity);
    }

    public List<ConversationMessageEntity> recentMessages(Long userId, String sessionId, int limit) {
        if (userId == null || !StringUtils.hasText(sessionId) || limit <= 0) {
            return Collections.emptyList();
        }
        List<ConversationMessageEntity> records = messageMapper.selectList(
                new LambdaQueryWrapper<ConversationMessageEntity>()
                        .eq(ConversationMessageEntity::getUserId, userId)
                        .eq(ConversationMessageEntity::getSessionId, sessionId)
                        .orderByDesc(ConversationMessageEntity::getCreatedAt)
                        .orderByDesc(ConversationMessageEntity::getId)
                        .last("LIMIT " + limit)
        );
        Collections.reverse(records);
        return records;
    }

    public ConversationStateEntity latestActiveState(Long userId, String sessionId) {
        if (userId == null || !StringUtils.hasText(sessionId)) {
            return null;
        }
        return stateMapper.selectOne(
                new LambdaQueryWrapper<ConversationStateEntity>()
                        .eq(ConversationStateEntity::getUserId, userId)
                        .eq(ConversationStateEntity::getSessionId, sessionId)
                        .eq(ConversationStateEntity::getStatus, ConversationConstants.STATUS_ACTIVE)
                        .gt(ConversationStateEntity::getExpireAt, LocalDateTime.now())
                        .orderByDesc(ConversationStateEntity::getCreatedAt)
                        .orderByDesc(ConversationStateEntity::getId)
                        .last("LIMIT 1")
        );
    }

    @Transactional
    public ConversationStateEntity createState(
            Long userId,
            String sessionId,
            String stateType,
            String taskId,
            String actionId,
            String confirmToken,
            Object context,
            int expireMinutes
    ) {
        closeActiveStates(userId, sessionId);
        ConversationStateEntity entity = new ConversationStateEntity();
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setStateType(stateType);
        entity.setStatus(ConversationConstants.STATUS_ACTIVE);
        entity.setTaskId(taskId);
        entity.setActionId(actionId);
        entity.setConfirmToken(confirmToken);
        entity.setContextJson(toJson(context));
        entity.setExpireAt(LocalDateTime.now().plusMinutes(expireMinutes));
        stateMapper.insert(entity);
        return entity;
    }

    public void closeActiveStates(Long userId, String sessionId) {
        if (userId == null || !StringUtils.hasText(sessionId)) {
            return;
        }
        List<ConversationStateEntity> states = stateMapper.selectList(
                new LambdaQueryWrapper<ConversationStateEntity>()
                        .eq(ConversationStateEntity::getUserId, userId)
                        .eq(ConversationStateEntity::getSessionId, sessionId)
                        .eq(ConversationStateEntity::getStatus, ConversationConstants.STATUS_ACTIVE)
        );
        for (ConversationStateEntity state : states) {
            closeState(state);
        }
    }

    public void closeState(ConversationStateEntity state) {
        if (state == null || state.getId() == null) {
            return;
        }
        ConversationStateEntity update = new ConversationStateEntity();
        update.setId(state.getId());
        update.setStatus(ConversationConstants.STATUS_CLOSED);
        stateMapper.updateById(update);
    }

    public String buildHistoryText(Long userId, String sessionId, int limit) {
        List<ConversationMessageEntity> messages = recentMessages(userId, sessionId, limit);
        if (messages.isEmpty()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder();
        for (ConversationMessageEntity message : messages) {
            builder.append(message.getRole()).append(": ").append(message.getContent()).append('\n');
        }
        return builder.toString().trim();
    }

    public String buildStateText(ConversationStateEntity state) {
        if (state == null) {
            return "无";
        }
        return "stateType=" + state.getStateType()
                + ", context=" + state.getContextJson();
    }

    public ConversationSessionContextEntity getSessionContext(Long userId, String sessionId) {
        if (userId == null || !StringUtils.hasText(sessionId)) {
            return null;
        }
        return sessionContextMapper.selectOne(
                new LambdaQueryWrapper<ConversationSessionContextEntity>()
                        .eq(ConversationSessionContextEntity::getUserId, userId)
                        .eq(ConversationSessionContextEntity::getSessionId, sessionId)
                        .last("LIMIT 1")
        );
    }

    @Transactional
    public void rememberLastMentionedEvent(Long userId, String sessionId, Long eventId) {
        if (userId == null || !StringUtils.hasText(sessionId) || eventId == null) {
            return;
        }
        ConversationSessionContextEntity context = getSessionContext(userId, sessionId);
        if (context == null) {
            context = new ConversationSessionContextEntity();
            context.setUserId(userId);
            context.setSessionId(sessionId);
            context.setLastMentionedEventId(eventId);
            sessionContextMapper.insert(context);
            return;
        }
        context.setLastMentionedEventId(eventId);
        sessionContextMapper.updateById(context);
    }

    public Long findLastMentionedEventId(Long userId, String sessionId) {
        ConversationSessionContextEntity context = getSessionContext(userId, sessionId);
        return context == null ? null : context.getLastMentionedEventId();
    }

    public String buildSessionContextText(Long userId, String sessionId) {
        Long eventId = findLastMentionedEventId(userId, sessionId);
        return eventId == null ? "无" : "lastMentionedEventId=" + eventId;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"json_serialize_failed\"}";
        }
    }
}
