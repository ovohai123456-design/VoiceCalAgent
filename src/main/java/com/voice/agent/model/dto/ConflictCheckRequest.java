package com.voice.agent.model.dto;

import java.time.LocalDateTime;

public class ConflictCheckRequest {
    private Long userId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long excludeEventId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Long getExcludeEventId() {
        return excludeEventId;
    }

    public void setExcludeEventId(Long excludeEventId) {
        this.excludeEventId = excludeEventId;
    }
}
