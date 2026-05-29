package com.voice.agent.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Agent 解析阶段的默认值补全器。
 *
 * <p>LLM 或规则解析只给出开始时间时，由这里补默认时长、默认用户等稳定规则。</p>
 */
@Component
public class DefaultValueResolver {
    @Value("${voicecal.default-user-id:1}")
    private Long defaultUserId;

    @Value("${voicecal.calendar.default-duration-minutes:60}")
    private Integer defaultDurationMinutes;

    @Value("${voicecal.calendar.default-reminder-minutes:10}")
    private Integer defaultReminderMinutes;

    public Long resolveUserId(Long userId) {
        return userId == null ? defaultUserId : userId;
    }

    public LocalDateTime resolveEndTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (endTime != null) {
            return endTime;
        }
        return startTime == null ? null : startTime.plusMinutes(defaultDurationMinutes);
    }

    public Integer resolveReminderMinutes(Integer reminderMinutes) {
        return reminderMinutes == null ? defaultReminderMinutes : reminderMinutes;
    }
}
