package com.voice.agent.model.dto;

import com.voice.agent.model.vo.CalendarEventVO;

public class UpdateEventActionPayload {
    private Long eventId;
    private UpdateEventRequest updateRequest;
    private CalendarEventVO originalEvent;

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

    public CalendarEventVO getOriginalEvent() { return originalEvent; }
    public void setOriginalEvent(CalendarEventVO originalEvent) { this.originalEvent = originalEvent; }
}
