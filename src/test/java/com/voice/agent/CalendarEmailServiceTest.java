package com.voice.agent;

import com.voice.agent.model.entity.CalendarEventEntity;
import com.voice.agent.model.entity.UserPreferenceEntity;
import com.voice.agent.service.CalendarEmailService;
import com.voice.agent.service.EmailDeliveryService;
import com.voice.agent.service.UserPreferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalendarEmailServiceTest {
    private final UserPreferenceService preferenceService = mock(UserPreferenceService.class);
    private final EmailDeliveryService emailDeliveryService = mock(EmailDeliveryService.class);
    private CalendarEmailService service;

    @BeforeEach
    void setUp() {
        service = new CalendarEmailService(preferenceService, emailDeliveryService);
        ReflectionTestUtils.setField(service, "creationNotificationEnabled", true);
        ReflectionTestUtils.setField(service, "reminderEnabled", true);
        when(emailDeliveryService.isEnabled()).thenReturn(true);
    }

    @Test
    void shouldBuildReminderPayloadUsingDefaultEmail() {
        when(preferenceService.get(1L)).thenReturn(preference("owner@example.com"));

        Map<String, Object> payload = service.buildReminderPayload(event(), null, null);

        assertEquals("owner@example.com", payload.get("receiver"));
        assertEquals("EVENT_REMINDER", payload.get("notification_type"));
        assertTrue(String.valueOf(payload.get("content")).contains("开始前 10 分钟"));
    }

    @Test
    void shouldSendCreationNotificationUsingRequestedEmail() {
        CalendarEventEntity event = event();

        service.notifyEventsCreatedAfterCommit(Collections.singletonList(event), "guest@example.com");

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(emailDeliveryService).send(
                eq("guest@example.com"),
                eq("VoiceCal 日程创建成功：project meeting"),
                content.capture()
        );
        assertTrue(content.getValue().contains("日程已创建成功"));
    }

    private UserPreferenceEntity preference(String email) {
        UserPreferenceEntity preference = new UserPreferenceEntity();
        preference.setUserId(1L);
        preference.setDefaultEmail(email);
        return preference;
    }

    private CalendarEventEntity event() {
        CalendarEventEntity event = new CalendarEventEntity();
        event.setId(100L);
        event.setUserId(1L);
        event.setTitle("project meeting");
        event.setDescription("review release checklist");
        event.setStartTime(LocalDateTime.of(2026, 6, 1, 10, 0));
        event.setEndTime(LocalDateTime.of(2026, 6, 1, 11, 0));
        event.setReminderMinutes(10);
        return event;
    }
}
