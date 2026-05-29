package com.voice.agent.model.vo;

import java.util.ArrayList;
import java.util.List;

public class ConflictResultVO {
    private Boolean hasConflict;
    private List<CalendarEventVO> conflictEvents = new ArrayList<>();
    private List<FreeSlotVO> suggestedSlots = new ArrayList<>();

    public Boolean getHasConflict() {
        return hasConflict;
    }

    public void setHasConflict(Boolean hasConflict) {
        this.hasConflict = hasConflict;
    }

    public List<CalendarEventVO> getConflictEvents() {
        return conflictEvents;
    }

    public void setConflictEvents(List<CalendarEventVO> conflictEvents) {
        this.conflictEvents = conflictEvents;
    }

    public List<FreeSlotVO> getSuggestedSlots() {
        return suggestedSlots;
    }

    public void setSuggestedSlots(List<FreeSlotVO> suggestedSlots) {
        this.suggestedSlots = suggestedSlots;
    }
}
