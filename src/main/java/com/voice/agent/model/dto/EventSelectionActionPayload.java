package com.voice.agent.model.dto;

import com.voice.agent.model.vo.CalendarEventVO;

import java.util.ArrayList;
import java.util.List;

public class EventSelectionActionPayload {
    private String actionType;
    private String sourceTaskId;
    private UpdateEventRequest updateRequest;
    private List<CalendarEventVO> candidates = new ArrayList<>();

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getSourceTaskId() { return sourceTaskId; }
    public void setSourceTaskId(String sourceTaskId) { this.sourceTaskId = sourceTaskId; }
    public UpdateEventRequest getUpdateRequest() { return updateRequest; }
    public void setUpdateRequest(UpdateEventRequest updateRequest) { this.updateRequest = updateRequest; }
    public List<CalendarEventVO> getCandidates() { return candidates; }
    public void setCandidates(List<CalendarEventVO> candidates) { this.candidates = candidates; }
}
