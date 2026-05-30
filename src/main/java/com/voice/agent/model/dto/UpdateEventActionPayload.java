package com.voice.agent.model.dto;

public class UpdateEventActionPayload {
    private Long eventId;
    private UpdateEventRequest updateRequest;

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public UpdateEventRequest getUpdateRequest() {
        return updateRequest;
    }

    public void setUpdateRequest(UpdateEventRequest updateRequest) {
        this.updateRequest = updateRequest;
    }
}
