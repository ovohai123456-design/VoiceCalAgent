package com.voice.agent.tool;

import com.voice.agent.skill.SkillDefinition;
import org.springframework.stereotype.Component;
import com.voice.agent.service.ReminderJobService;

import java.util.Map;

@Component
public class SchedulerToolAdapter implements ToolAdapter {
    private final ReminderJobService reminderJobService;

    public SchedulerToolAdapter(ReminderJobService reminderJobService) {
        this.reminderJobService = reminderJobService;
    }
    public String getType() {
        return "scheduler";
    }

    public ToolExecutionResult execute(SkillDefinition skill, Map<String, Object> arguments) {
        if ("EMAIL".equals(skill.getExecutor().getJobType())) {
            return ToolExecutionResult.success(skill.getSkillId(), reminderJobService.scheduleEmail(arguments));
        }
        return ToolExecutionResult.failed(skill.getSkillId(), "Unsupported scheduler job type: " + skill.getExecutor().getJobType());
    }
}
