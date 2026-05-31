package com.voice.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voice.agent.mapper.ReminderJobMapper;
import com.voice.agent.model.entity.CalendarEventEntity;
import com.voice.agent.model.entity.ReminderJobEntity;
import com.voice.agent.service.CalendarEmailService;
import com.voice.agent.service.EmailDeliveryService;
import com.voice.agent.service.ReminderJobService;
import com.voice.agent.stream.AgentEventStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderJobServiceTest {
    private final ReminderJobMapper reminderJobMapper = mock(ReminderJobMapper.class);
    private final EmailDeliveryService emailDeliveryService = mock(EmailDeliveryService.class);
    private final CalendarEmailService calendarEmailService = mock(CalendarEmailService.class);
    private ReminderJobService service;

    @BeforeEach
    void setUp() {
        reset(reminderJobMapper, emailDeliveryService, calendarEmailService);
        service = new ReminderJobService(
                reminderJobMapper,
                emailDeliveryService,
                calendarEmailService,
                new ObjectMapper(),
                mock(AgentEventStreamService.class)
        );
        ReflectionTestUtils.setField(service, "maxRetryCount", 3);
    }

    @Test
    void shouldCreatePendingReminderBeforeEventStarts() {
        CalendarEventEntity event = event();

        service.syncForEvent(event);

        ArgumentCaptor<ReminderJobEntity> captor = ArgumentCaptor.forClass(ReminderJobEntity.class);
        verify(reminderJobMapper).insert(captor.capture());
        ReminderJobEntity inserted = captor.getValue();
        assertEquals(100L, inserted.getEventId());
        assertEquals("PENDING", inserted.getStatus());
        assertEquals(event.getStartTime().minusMinutes(10), inserted.getRunAt());
        assertTrue(inserted.getJobPayloadJson().contains("project meeting"));
    }

    @Test
    void shouldCreateEmailReminderWhenEmailPayloadIsAvailable() {
        CalendarEventEntity event = event();
        Map<String, Object> payload = emailPayload(event);
        when(calendarEmailService.buildReminderPayload(eq(event), isNull(), isNull())).thenReturn(payload);

        service.createForEvent(event);

        ArgumentCaptor<ReminderJobEntity> captor = ArgumentCaptor.forClass(ReminderJobEntity.class);
        verify(reminderJobMapper, times(2)).insert(captor.capture());
        List<ReminderJobEntity> jobs = captor.getAllValues();
        assertEquals("IN_APP", jobs.get(0).getJobType());
        assertEquals("EMAIL", jobs.get(1).getJobType());
        assertTrue(jobs.get(1).getJobPayloadJson().contains("demo@example.com"));
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
    void shouldSendDueEmailThroughSmtpDeliveryService() throws Exception {
        CalendarEventEntity event = event();
        ReminderJobEntity job = new ReminderJobEntity();
        job.setId(10L);
        job.setEventId(event.getId());
        job.setJobType("EMAIL");
        job.setJobPayloadJson(new ObjectMapper().writeValueAsString(emailPayload(event)));
        job.setStatus("PENDING");
        when(reminderJobMapper.selectList(any())).thenReturn(Collections.singletonList(job));

        service.executeDueJobs();

        assertEquals("EXECUTED", job.getStatus());
        verify(emailDeliveryService).send(anyMap());
    }

    @Test
    void shouldReuseEquivalentPendingEmailJob() throws Exception {
        CalendarEventEntity event = event();
        LocalDateTime runAt = event.getStartTime().minusMinutes(event.getReminderMinutes());
        Map<String, Object> arguments = emailPayload(event);
        arguments.put("run_at", runAt.toString());
        ReminderJobEntity existing = new ReminderJobEntity();
        existing.setId(20L);
        existing.setEventId(event.getId());
        existing.setJobPayloadJson(new ObjectMapper().writeValueAsString(arguments));
        when(emailDeliveryService.isEnabled()).thenReturn(true);
        when(reminderJobMapper.selectList(any())).thenReturn(Collections.singletonList(existing));

        ReminderJobEntity result = service.scheduleEmail(arguments);

        assertSame(existing, result);
        verify(reminderJobMapper, never()).insert(any(ReminderJobEntity.class));
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

    private CalendarEventEntity event() {
        CalendarEventEntity event = new CalendarEventEntity();
        event.setId(100L);
        event.setUserId(1L);
        event.setTitle("project meeting");
        event.setStartTime(LocalDateTime.now().plusHours(2));
        event.setEndTime(event.getStartTime().plusHours(1));
        event.setReminderMinutes(10);
        return event;
    }

    private Map<String, Object> emailPayload(CalendarEventEntity event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", event.getUserId());
        payload.put("event_id", event.getId());
        payload.put("receiver", "demo@example.com");
        payload.put("subject", "VoiceCal reminder");
        payload.put("content", "meeting reminder");
        return payload;
    }
}
