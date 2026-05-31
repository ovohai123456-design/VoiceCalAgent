package com.voice.agent;

import com.voice.agent.mapper.CalendarEventMapper;
import com.voice.agent.mapper.RecurrenceSeriesMapper;
import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.entity.CalendarEventEntity;
import com.voice.agent.model.entity.RecurrenceSeriesEntity;
import com.voice.agent.model.vo.CalendarEventVO;
import com.voice.agent.service.ReminderJobService;
import com.voice.agent.service.impl.CalendarServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalendarServiceImplTest {
    private final CalendarEventMapper calendarEventMapper = mock(CalendarEventMapper.class);
    private final RecurrenceSeriesMapper recurrenceSeriesMapper = mock(RecurrenceSeriesMapper.class);
    private final ReminderJobService reminderJobService = mock(ReminderJobService.class);
    private CalendarServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CalendarServiceImpl(calendarEventMapper, recurrenceSeriesMapper, reminderJobService);
        ReflectionTestUtils.setField(service, "defaultUserId", 1L);
        ReflectionTestUtils.setField(service, "recurrenceDefaultCount", 12);
        ReflectionTestUtils.setField(service, "recurrenceMaxCount", 100);
    }

    @Test
    void shouldBatchCreateWeeklyRecurringEvents() {
        when(calendarEventMapper.selectList(any())).thenReturn(
                Collections.emptyList(),
                Arrays.asList(created(10L, 0), created(11L, 1), created(12L, 2))
        );

        CreateEventRequest request = new CreateEventRequest();
        request.setUserId(1L);
        request.setTitle("weekly meeting");
        request.setStartTime(LocalDateTime.of(2026, 6, 1, 10, 0));
        request.setEndTime(LocalDateTime.of(2026, 6, 1, 11, 0));
        request.setRecurrenceType("WEEKLY");
        request.setRecurrenceCount(3);

        CalendarEventVO result = service.createPreparedEvent(request);

        ArgumentCaptor<List<CalendarEventEntity>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(calendarEventMapper).insertBatch(eventsCaptor.capture());
        List<CalendarEventEntity> inserted = eventsCaptor.getValue();
        assertEquals(3, inserted.size());
        assertEquals(LocalDateTime.of(2026, 6, 1, 10, 0), inserted.get(0).getStartTime());
        assertEquals(LocalDateTime.of(2026, 6, 8, 10, 0), inserted.get(1).getStartTime());
        assertEquals(LocalDateTime.of(2026, 6, 15, 10, 0), inserted.get(2).getStartTime());
        assertNotNull(inserted.get(0).getRecurrenceSeriesId());
        verify(recurrenceSeriesMapper).insert(any(RecurrenceSeriesEntity.class));
        verify(reminderJobService).createForEvents(any());
        assertEquals(10L, result.getId());
    }

    @Test
    void shouldDeleteEveryOccurrenceInSeries() {
        CalendarEventEntity event = created(10L, 0);
        when(calendarEventMapper.selectOne(any())).thenReturn(event);
        when(calendarEventMapper.softDeleteSeries("series_test", 1L)).thenReturn(3);
        when(recurrenceSeriesMapper.selectById("series_test")).thenReturn(new RecurrenceSeriesEntity());

        assertEquals(true, service.deleteEvent(10L, 1L, "SERIES"));

        verify(calendarEventMapper).softDeleteSeries("series_test", 1L);
        verify(reminderJobService).deleteForSeries("series_test");
    }

    private CalendarEventEntity created(Long id, Integer index) {
        CalendarEventEntity event = new CalendarEventEntity();
        event.setId(id);
        event.setUserId(1L);
        event.setTitle("weekly meeting");
        event.setStartTime(LocalDateTime.of(2026, 6, 1, 10, 0).plusWeeks(index));
        event.setEndTime(LocalDateTime.of(2026, 6, 1, 11, 0).plusWeeks(index));
        event.setRecurrenceSeriesId("series_test");
        event.setRecurrenceIndex(index);
        return event;
    }
}
