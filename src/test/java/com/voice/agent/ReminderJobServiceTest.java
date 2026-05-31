package com.voice.agent;

import com.voice.agent.mapper.ReminderJobMapper;
import com.voice.agent.model.entity.CalendarEventEntity;
import com.voice.agent.model.entity.ReminderJobEntity;
import com.voice.agent.service.ReminderJobService;
import com.voice.agent.mock.MockEmailProvider;
import com.voice.agent.stream.AgentEventStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderJobServiceTest {
    private final ReminderJobMapper reminderJobMapper = mock(ReminderJobMapper.class);
    private ReminderJobService service;

    @BeforeEach
    void setUp() {
        service = new ReminderJobService(
                reminderJobMapper,
                mock(MockEmailProvider.class),
                new ObjectMapper(),
                mock(AgentEventStreamService.class)
        );
        ReflectionTestUtils.setField(service, "maxRetryCount", 3);
    }

    @Test
    void shouldCreatePendingReminderBeforeEventStarts() {
        CalendarEventEntity event = new CalendarEventEntity();
        event.setId(100L);
        event.setUserId(1L);
        event.setTitle("项目会");
        event.setStartTime(LocalDateTime.now().plusHours(2));
        event.setReminderMinutes(10);

        service.syncForEvent(event);

        ArgumentCaptor<ReminderJobEntity> captor = ArgumentCaptor.forClass(ReminderJobEntity.class);
        verify(reminderJobMapper).insert(captor.capture());
        ReminderJobEntity inserted = captor.getValue();
        assertEquals(100L, inserted.getEventId());
        assertEquals("PENDING", inserted.getStatus());
        assertEquals(event.getStartTime().minusMinutes(10), inserted.getRunAt());
        assertTrue(inserted.getJobPayloadJson().contains("项目会"));
    }

    @Test
    void shouldMarkDueReminderExecuted() {
        ReminderJobEntity job = new ReminderJobEntity();
        job.setId(10L);
        job.setEventId(100L);
        job.setJobType("IN_APP");
        job.setStatus("PENDING");
        when(reminderJobMapper.selectList(any())).thenReturn(Collections.singletonList(job));

        service.executeDueJobs();

        assertEquals("EXECUTED", job.getStatus());
        verify(reminderJobMapper).updateById(job);
    }

    @Test
    void shouldDeleteReminderJobsForDeletedEvent() {
        when(reminderJobMapper.delete(any())).thenReturn(2);

        assertEquals(2, service.deleteForEvent(100L));

        verify(reminderJobMapper).delete(any());
    }

    @Test
    void shouldDeleteEveryReminderJobForUser() {
        when(reminderJobMapper.delete(any())).thenReturn(3);

        assertEquals(3, service.clearByUser(1L));

        verify(reminderJobMapper).delete(any());
    }
}
