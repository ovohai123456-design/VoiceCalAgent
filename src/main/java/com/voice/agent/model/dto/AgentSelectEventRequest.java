package com.voice.agent.model.dto;

public class AgentSelectEventRequest {
    private Long userId;
    private String sessionId;
    private String confirmToken;
    private Integer candidateIndex;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getConfirmToken() { return confirmToken; }
    public void setConfirmToken(String confirmToken) { this.confirmToken = confirmToken; }
    public Integer getCandidateIndex() { return candidateIndex; }
    public void setCandidateIndex(Integer candidateIndex) { this.candidateIndex = candidateIndex; }
}
