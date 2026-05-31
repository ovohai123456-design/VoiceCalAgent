package com.voice.agent.stream;

import com.voice.agent.agent.AgentConstants;
import com.voice.agent.model.entity.CommandTaskEntity;
import com.voice.agent.model.vo.ExecutionLogVO;
import com.voice.agent.model.vo.ReminderJobVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps lightweight in-process SSE subscriptions for the current application instance.
 *
 * <p>When the application is deployed with multiple instances, fan-out should be
 * backed by Redis pub/sub or another message broker while this class remains the
 * connection writer for each instance.</p>
 */
@Service
@Slf4j
public class AgentEventStreamService {
    private static final long NO_TIMEOUT = 0L;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();
    private final Map<String, Long> taskOwners = new ConcurrentHashMap<>();
    private final AtomicLong eventSequence = new AtomicLong();

    public SseEmitter subscribe(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }

        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByUser.computeIfAbsent(
                userId,
                ignored -> new CopyOnWriteArrayList<>()
        );
        emitters.add(emitter);
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(error -> removeEmitter(userId, emitter));
        send(userId, emitter, "connected", Collections.singletonMap("connectedAt", LocalDateTime.now()));
        return emitter;
    }

    public void registerTask(CommandTaskEntity task) {
        if (task == null || task.getTaskId() == null || task.getUserId() == null) {
            return;
        }
        taskOwners.put(task.getTaskId(), task.getUserId());
        publishTaskStatus(task.getTaskId(), task.getStatus());
    }

    public void publishTaskStatus(String taskId, String status) {
        Long userId = taskOwners.get(taskId);
        if (userId == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", taskId);
        payload.put("status", status);
        publish(userId, "task-status", payload);
        if (isTerminalTaskStatus(status)) {
            taskOwners.remove(taskId, userId);
        }
    }

    public void publishWorkflowStep(String taskId, ExecutionLogVO step) {
        Long userId = taskOwners.get(taskId);
        if (userId != null && step != null) {
            publish(userId, "workflow-step", step);
        }
    }

    public void publishReminderChanged(Long userId, ReminderJobVO reminder) {
        if (userId != null && reminder != null) {
            publish(userId, "reminder-changed", reminder);
        }
    }

    public void publishRemindersRefresh(Long userId) {
        if (userId != null) {
            publish(userId, "reminders-refresh", Collections.singletonMap("userId", userId));
        }
    }

    @Scheduled(fixedDelayString = "${voicecal.sse.heartbeat-ms:15000}")
    public void sendHeartbeat() {
        for (Map.Entry<Long, CopyOnWriteArrayList<SseEmitter>> entry : emittersByUser.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                send(entry.getKey(), emitter, "heartbeat", Collections.singletonMap("time", LocalDateTime.now()));
            }
        }
    }

    private void publish(Long userId, String eventName, Object payload) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            send(userId, emitter, eventName, payload);
        }
    }

    private boolean isTerminalTaskStatus(String status) {
        return AgentConstants.STATUS_SUCCESS.equals(status)
                || AgentConstants.STATUS_FAILED.equals(status)
                || AgentConstants.STATUS_CANCELED.equals(status);
    }

    private void send(Long userId, SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(eventSequence.incrementAndGet()))
                    .name(eventName)
                    .data(payload));
        } catch (IOException | IllegalStateException e) {
            removeEmitter(userId, emitter);
            log.debug("SSE client disconnected userId={} event={}", userId, eventName);
        }
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUser.remove(userId, emitters);
        }
    }
}
