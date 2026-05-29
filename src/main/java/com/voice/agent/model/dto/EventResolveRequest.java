package com.voice.agent.model.dto;

import java.time.LocalDateTime;

public class EventResolveRequest {
    private Long userId;
    private String titleKeyword;
    private LocalDateTime rangeStart;
    private LocalDateTime rangeEnd;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitleKeyword() {
        return titleKeyword;
    }

    public void setTitleKeyword(String titleKeyword) {
        this.titleKeyword = titleKeyword;
    }

    public LocalDateTime getRangeStart() {
        return rangeStart;
    }

    public void setRangeStart(LocalDateTime rangeStart) {
        this.rangeStart = rangeStart;
    }

    public LocalDateTime getRangeEnd() {
        return rangeEnd;
    }

    public void setRangeEnd(LocalDateTime rangeEnd) {
        this.rangeEnd = rangeEnd;
    }
}
