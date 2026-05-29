package com.voice.agent.service;

import com.voice.agent.model.dto.ConflictCheckRequest;
import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.dto.EventResolveRequest;
import com.voice.agent.model.dto.FreeSlotRequest;
import com.voice.agent.model.dto.QueryEventRequest;
import com.voice.agent.model.dto.UpdateEventRequest;
import com.voice.agent.model.vo.CalendarEventVO;
import com.voice.agent.model.vo.ConflictResultVO;
import com.voice.agent.model.vo.FreeSlotVO;

import java.util.List;

public interface CalendarService {
    CalendarEventVO createEvent(CreateEventRequest request);

    List<CalendarEventVO> queryEvents(QueryEventRequest request);

    CalendarEventVO updateEvent(Long eventId, UpdateEventRequest request);

    Boolean deleteEvent(Long eventId, Long userId);

    ConflictResultVO checkConflict(ConflictCheckRequest request);

    List<FreeSlotVO> findFreeSlots(FreeSlotRequest request);

    List<CalendarEventVO> findCandidateEvents(EventResolveRequest request);
}
