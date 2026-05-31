package com.voice.agent.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class EventResolveRequest {
    private Long eventId;
    private Long userId;
    private String titleKeyword;
    private String reference;
    private Boolean deleteAllMatches;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime rangeStart;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime rangeEnd;

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

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

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public Boolean getDeleteAllMatches() {
        return deleteAllMatches;
    }

    public void setDeleteAllMatches(Boolean deleteAllMatches) {
        this.deleteAllMatches = deleteAllMatches;
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
