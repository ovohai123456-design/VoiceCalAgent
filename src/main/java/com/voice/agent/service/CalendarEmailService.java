package com.voice.agent.service;

import com.voice.agent.model.entity.CalendarEventEntity;
import com.voice.agent.model.entity.UserPreferenceEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class CalendarEmailService {
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserPreferenceService preferenceService;
    private final EmailDeliveryService emailDeliveryService;

    @Value("${voicecal.email.creation-notification-enabled:true}")
    private boolean creationNotificationEnabled;

    @Value("${voicecal.email.reminder-enabled:true}")
    private boolean reminderEnabled;

    public CalendarEmailService(
            UserPreferenceService preferenceService,
            EmailDeliveryService emailDeliveryService
    ) {
        this.preferenceService = preferenceService;
        this.emailDeliveryService = emailDeliveryService;
    }

    public Map<String, Object> buildReminderPayload(
            CalendarEventEntity event,
            String requestedReceiver,
            String requestedContent
    ) {
        if (!reminderEnabled || !emailDeliveryService.isEnabled()) {
            return null;
        }
        String receiver = resolveReceiver(event.getUserId(), requestedReceiver);
        if (!StringUtils.hasText(receiver)) {
            return null;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", event.getUserId());
        payload.put("event_id", event.getId());
        payload.put("receiver", receiver);
        payload.put("title", event.getTitle());
        payload.put("description", event.getDescription());
        payload.put("subject", "VoiceCal 日程提醒：" + event.getTitle());
        payload.put("content", StringUtils.hasText(requestedContent)
                ? requestedContent.trim()
                : buildReminderContent(event));
        payload.put("notification_type", "EVENT_REMINDER");
        return payload;
    }

    public void notifyEventsCreatedAfterCommit(List<CalendarEventEntity> events, String requestedReceiver) {
        if (events == null || events.isEmpty()) {
            return;
        }
        runAfterCommit(() -> sendCreationNotification(events, requestedReceiver));
    }

    private void sendCreationNotification(List<CalendarEventEntity> events, String requestedReceiver) {
        if (!creationNotificationEnabled || !emailDeliveryService.isEnabled()) {
            return;
        }
        CalendarEventEntity firstEvent = events.get(0);
        String receiver = resolveReceiver(firstEvent.getUserId(), requestedReceiver);
        if (!StringUtils.hasText(receiver)) {
            return;
        }
        try {
            emailDeliveryService.send(
                    receiver,
                    "VoiceCal 日程创建成功：" + firstEvent.getTitle(),
                    buildCreationContent(events)
            );
        } catch (RuntimeException e) {
            log.warn("Calendar creation email failed eventId={} receiver={} error={}",
                    firstEvent.getId(), receiver, e.getMessage());
        }
    }

    private String resolveReceiver(Long userId, String requestedReceiver) {
        if (StringUtils.hasText(requestedReceiver)) {
            return requestedReceiver.trim();
        }
        if (userId == null) {
            return null;
        }
        UserPreferenceEntity preference = preferenceService.get(userId);
        return preference != null && StringUtils.hasText(preference.getDefaultEmail())
                ? preference.getDefaultEmail().trim()
                : null;
    }

    private String buildCreationContent(List<CalendarEventEntity> events) {
        CalendarEventEntity event = events.get(0);
        StringBuilder content = new StringBuilder("日程已创建成功。\n\n");
        appendEventDetails(content, event);
        if (events.size() > 1) {
            content.append("\n重复日程数量：").append(events.size()).append("\n");
        }
        appendReminderDetails(content, event);
        return content.toString();
    }

    private String buildReminderContent(CalendarEventEntity event) {
        StringBuilder content = new StringBuilder("日程即将开始。\n\n");
        appendEventDetails(content, event);
        appendReminderDetails(content, event);
        return content.toString();
    }

    private void appendEventDetails(StringBuilder content, CalendarEventEntity event) {
        content.append("标题：").append(event.getTitle()).append("\n")
                .append("开始时间：").append(event.getStartTime().format(DISPLAY_TIME)).append("\n")
                .append("结束时间：").append(event.getEndTime().format(DISPLAY_TIME)).append("\n");
        if (StringUtils.hasText(event.getLocation())) {
            content.append("地点：").append(event.getLocation()).append("\n");
        }
        if (StringUtils.hasText(event.getMeetingUrl())) {
            content.append("会议链接：").append(event.getMeetingUrl()).append("\n");
        }
    }

    private void appendReminderDetails(StringBuilder content, CalendarEventEntity event) {
        if (event.getReminderMinutes() != null && event.getReminderMinutes() > 0) {
            content.append("\n提醒时间：日程开始前 ")
                    .append(event.getReminderMinutes())
                    .append(" 分钟\n");
        }
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
