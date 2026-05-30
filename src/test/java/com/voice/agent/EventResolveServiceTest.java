package com.voice.agent;

import com.voice.agent.agent.EventResolveService;
import com.voice.agent.model.dto.EventResolveRequest;
import com.voice.agent.model.vo.CalendarEventVO;
import com.voice.agent.service.CalendarService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventResolveServiceTest {
    private final CalendarService calendarService = mock(CalendarService.class);
    private final EventResolveService service = new EventResolveService(calendarService);
    private final EventResolveRequest request = new EventResolveRequest();

    @Test
    void shouldReturnOnlyCandidate() {
        CalendarEventVO event = event(1L);
        when(calendarService.findCandidateEvents(request)).thenReturn(Collections.singletonList(event));

        assertEquals(event, service.resolveSingle(request));
    }

    @Test
    void shouldRejectAmbiguousCandidates() {
        when(calendarService.findCandidateEvents(request)).thenReturn(Arrays.asList(event(1L), event(2L)));

        assertThrows(IllegalArgumentException.class, () -> service.resolveSingle(request));
    }

    private CalendarEventVO event(Long id) {
        CalendarEventVO event = new CalendarEventVO();
        event.setId(id);
        return event;
    }
}
