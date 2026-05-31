package com.voice.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.voice.agent.mapper.ReminderJobMapper;
import com.voice.agent.model.entity.CalendarEventEntity;
import com.voice.agent.model.entity.ReminderJobEntity;
import com.voice.agent.model.vo.ReminderJobVO;
import com.voice.agent.mock.MockEmailProvider;
import com.voice.agent.stream.AgentEventStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReminderJobService {
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_EXECUTED = "EXECUTED";
    private static final String STATUS_CANCELED = "CANCELED";
    private static final String JOB_TYPE_IN_APP = "IN_APP";
    private static final String JOB_TYPE_EMAIL = "EMAIL";

    private final ReminderJobMapper reminderJobMapper;
    private final MockEmailProvider mockEmailProvider;
    private final ObjectMapper objectMapper;
    private final AgentEventStreamService eventStreamService;

    @Value("${voicecal.scheduler.max-retry-count:3}")
    private Integer maxRetryCount;

    public ReminderJobService(
            ReminderJobMapper reminderJobMapper,
            MockEmailProvider mockEmailProvider,
            ObjectMapper objectMapper,
            AgentEventStreamService eventStreamService
    ) {
        this.reminderJobMapper = reminderJobMapper;
        this.mockEmailProvider = mockEmailProvider;
        this.objectMapper = objectMapper;
        this.eventStreamService = eventStreamService;
    }

    @Transactional
    public void syncForEvent(CalendarEventEntity event) {
        deleteForEvent(event.getId());
        createForEvent(event);
    }

    @Transactional
    public void createForEvent(CalendarEventEntity event) {
        ReminderJobEntity job = buildJob(event, LocalDateTime.now());
        if (job == null) {
            return;
        }
        reminderJobMapper.insert(job);
        publishReminderChangedAfterCommit(job);
    }

    @Transactional
    public void createForEvents(List<CalendarEventEntity> events) {
        LocalDateTime now = LocalDateTime.now();
        List<ReminderJobEntity> jobs = new ArrayList<>();
        for (CalendarEventEntity event : events) {
            ReminderJobEntity job = buildJob(event, now);
            if (job != null) {
                jobs.add(job);
            }
        }
        if (!jobs.isEmpty()) {
            reminderJobMapper.insertBatch(jobs);
            Set<Long> userIds = new HashSet<>();
            for (ReminderJobEntity job : jobs) {
                userIds.add(job.getUserId());
            }
            for (Long userId : userIds) {
                publishRemindersRefreshAfterCommit(userId);
            }
        }
    }

    private ReminderJobEntity buildJob(CalendarEventEntity event, LocalDateTime now) {
        if (event.getReminderMinutes() == null || event.getReminderMinutes() <= 0) {
            return null;
        }
        LocalDateTime runAt = event.getStartTime().minusMinutes(event.getReminderMinutes());
        if (!runAt.isAfter(now)) {
            return null;
        }
        ReminderJobEntity job = new ReminderJobEntity();
        job.setUserId(event.getUserId());
        job.setEventId(event.getId());
        job.setJobType(JOB_TYPE_IN_APP);
        job.setJobPayloadJson("{\"title\":\"" + escapeJson(event.getTitle()) + "\"}");
        job.setRunAt(runAt);
        job.setStatus(STATUS_PENDING);
        job.setRetryCount(0);
        job.setMaxRetryCount(maxRetryCount);
        return job;
    }

    @Transactional
    public void cancelPendingForEvent(Long eventId) {
        if (eventId == null) {
            return;
        }
        ReminderJobEntity update = new ReminderJobEntity();
        update.setStatus(STATUS_CANCELED);
        reminderJobMapper.update(
                update,
                Wrappers.lambdaUpdate(ReminderJobEntity.class)
                        .eq(ReminderJobEntity::getEventId, eventId)
                        .eq(ReminderJobEntity::getStatus, STATUS_PENDING)
        );
    }

    @Transactional
    public void cancelPendingForSeries(String seriesId) {
        if (seriesId != null && !seriesId.trim().isEmpty()) {
            reminderJobMapper.cancelPendingForSeries(seriesId);
        }
    }

    @Transactional
    public int deleteForEvent(Long eventId) {
        if (eventId == null) {
            return 0;
        }
        List<ReminderJobEntity> jobs = reminderJobMapper.selectList(
                Wrappers.lambdaQuery(ReminderJobEntity.class)
                        .eq(ReminderJobEntity::getEventId, eventId)
        );
        int deleted = reminderJobMapper.delete(
                Wrappers.lambdaQuery(ReminderJobEntity.class)
                        .eq(ReminderJobEntity::getEventId, eventId)
        );
        if (deleted > 0) {
            jobs.stream()
                    .map(ReminderJobEntity::getUserId)
                    .distinct()
                    .forEach(this::publishRemindersRefreshAfterCommit);
        }
        return deleted;
    }

    @Transactional
    public int deleteForSeries(String seriesId) {
        if (seriesId == null || seriesId.trim().isEmpty()) {
            return 0;
        }
        String normalizedSeriesId = seriesId.trim();
        List<Long> userIds = reminderJobMapper.selectUserIdsForSeries(normalizedSeriesId);
        int deleted = reminderJobMapper.deleteForSeries(normalizedSeriesId);
        if (deleted > 0) {
            userIds.forEach(this::publishRemindersRefreshAfterCommit);
        }
        return deleted;
    }

    @Transactional
    public int deleteById(Long reminderId, Long userId) {
        if (reminderId == null) {
            throw new IllegalArgumentException("reminderId 不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        int deleted = reminderJobMapper.delete(
                Wrappers.lambdaQuery(ReminderJobEntity.class)
                        .eq(ReminderJobEntity::getId, reminderId)
                        .eq(ReminderJobEntity::getUserId, userId)
        );
        if (deleted > 0) {
            publishRemindersRefreshAfterCommit(userId);
        }
        return deleted;
    }

    @Transactional
    public int clearByUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        int deleted = reminderJobMapper.delete(
                Wrappers.lambdaQuery(ReminderJobEntity.class)
                        .eq(ReminderJobEntity::getUserId, userId)
        );
        if (deleted > 0) {
            publishRemindersRefreshAfterCommit(userId);
        }
        return deleted;
    }

    public List<ReminderJobVO> listByUser(Long userId) {
        return reminderJobMapper.selectList(
                Wrappers.lambdaQuery(ReminderJobEntity.class)
                        .eq(ReminderJobEntity::getUserId, userId)
                        .orderByAsc(ReminderJobEntity::getRunAt)
        ).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Scheduled(fixedDelayString = "${voicecal.scheduler.scan-fixed-rate-ms:60000}")
    @Transactional
    public void executeDueJobs() {
        List<ReminderJobEntity> dueJobs = reminderJobMapper.selectList(
                Wrappers.lambdaQuery(ReminderJobEntity.class)
                        .eq(ReminderJobEntity::getStatus, STATUS_PENDING)
                        .le(ReminderJobEntity::getRunAt, LocalDateTime.now())
                        .orderByAsc(ReminderJobEntity::getRunAt)
                        .last("LIMIT 100")
        );
        for (ReminderJobEntity job : dueJobs) {
            executeJob(job);
        }
    }

    @Transactional
    public ReminderJobEntity scheduleEmail(Map<String, Object> arguments) {
        ReminderJobEntity job = new ReminderJobEntity();
        job.setUserId(asLong(arguments.get("user_id")));
        job.setEventId(asLong(arguments.get("event_id")));
        job.setJobType(JOB_TYPE_EMAIL);
        job.setJobPayloadJson(toJson(arguments));
        job.setRunAt(LocalDateTime.parse(String.valueOf(arguments.get("run_at"))));
        job.setStatus(STATUS_PENDING);
        job.setRetryCount(0);
        job.setMaxRetryCount(maxRetryCount);
        reminderJobMapper.insert(job);
        publishReminderChangedAfterCommit(job);
        return job;
    }

    private void executeJob(ReminderJobEntity job) {
        try {
            if (JOB_TYPE_EMAIL.equals(job.getJobType())) {
                mockEmailProvider.send(readPayload(job.getJobPayloadJson()));
            }
            job.setStatus(STATUS_EXECUTED);
            job.setExecutedAt(LocalDateTime.now());
            reminderJobMapper.updateById(job);
            publishReminderChangedAfterCommit(job);
            log.info("Reminder executed jobId={} eventId={} type={}", job.getId(), job.getEventId(), job.getJobType());
        } catch (RuntimeException e) {
            int retryCount = job.getRetryCount() == null ? 1 : job.getRetryCount() + 1;
            job.setRetryCount(retryCount);
            job.setLastError(e.getMessage());
            job.setStatus(retryCount >= job.getMaxRetryCount() ? "FAILED" : STATUS_PENDING);
            reminderJobMapper.updateById(job);
            publishReminderChangedAfterCommit(job);
            log.warn("Reminder failed jobId={} type={} retry={}", job.getId(), job.getJobType(), retryCount);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPayload(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("提醒任务 payload 解析失败", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("提醒任务 payload 序列化失败", e);
        }
    }

    private Long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value));
    }

    private ReminderJobVO toVO(ReminderJobEntity entity) {
        ReminderJobVO vo = new ReminderJobVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setEventId(entity.getEventId());
        vo.setJobType(entity.getJobType());
        vo.setJobPayloadJson(entity.getJobPayloadJson());
        vo.setRunAt(entity.getRunAt());
        vo.setStatus(entity.getStatus());
        vo.setRetryCount(entity.getRetryCount());
        vo.setMaxRetryCount(entity.getMaxRetryCount());
        vo.setLastError(entity.getLastError());
        vo.setExecutedAt(entity.getExecutedAt());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private void publishReminderChangedAfterCommit(ReminderJobEntity job) {
        ReminderJobVO reminder = toVO(job);
        runAfterCommit(() -> eventStreamService.publishReminderChanged(job.getUserId(), reminder));
    }

    private void publishRemindersRefreshAfterCommit(Long userId) {
        runAfterCommit(() -> eventStreamService.publishRemindersRefresh(userId));
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

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
