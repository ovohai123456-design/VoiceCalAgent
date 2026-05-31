package com.voice.agent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("conversation_session_context")
public class ConversationSessionContextEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String sessionId;
    private Long lastMentionedEventId;
    private String lastQueryEventIdsJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Long getLastMentionedEventId() { return lastMentionedEventId; }
    public void setLastMentionedEventId(Long lastMentionedEventId) { this.lastMentionedEventId = lastMentionedEventId; }
    public String getLastQueryEventIdsJson() { return lastQueryEventIdsJson; }
    public void setLastQueryEventIdsJson(String lastQueryEventIdsJson) { this.lastQueryEventIdsJson = lastQueryEventIdsJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
