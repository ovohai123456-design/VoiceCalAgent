package com.voice.agent.agent;

import com.voice.agent.model.dto.EventResolveRequest;
import com.voice.agent.model.vo.CalendarEventVO;
import com.voice.agent.service.CalendarService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventResolveService {
    private final CalendarService calendarService;

    public EventResolveService(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    public CalendarEventVO resolveSingle(EventResolveRequest request) {
        List<CalendarEventVO> candidates = calendarService.findCandidateEvents(request);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("没有找到符合条件的日程");
        }
        if (candidates.size() > 1) {
            throw new AmbiguousEventException(candidates);
        }
        return candidates.get(0);
    }
}
