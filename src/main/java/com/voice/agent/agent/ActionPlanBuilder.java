package com.voice.agent.agent;

import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.dto.UpdateEventRequest;
import com.voice.agent.model.vo.CalendarEventVO;
import com.voice.agent.tool.ToolActionStep;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ActionPlanBuilder {
    public List<ToolActionStep> buildBeforeCalendarCreate(CreateEventRequest request) {
        List<ToolActionStep> steps = new ArrayList<>();
        if (!Boolean.TRUE.equals(request.getOnlineMeeting()) && !hasMeetingCreate(request.getPlannedToolSteps())) {
            return steps;
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("title", request.getTitle());
        arguments.put("start_time", request.getStartTime());
        arguments.put("end_time", request.getEndTime());
        steps.add(ToolActionStep.of(10, "meeting.create", "meeting", arguments));
        return steps;
    }

    public List<ToolActionStep> buildAfterCalendarCreate(CreateEventRequest request, CalendarEventVO event) {
        List<ToolActionStep> steps = new ArrayList<>();
        if (StringUtils.hasText(request.getSmsReceiver())) {
            Map<String, Object> contactArguments = new LinkedHashMap<>();
            contactArguments.put("user_id", request.getUserId());
            contactArguments.put("keyword", request.getSmsReceiver());
            steps.add(ToolActionStep.of(20, "contact.query", "contact", contactArguments));

            Map<String, Object> smsArguments = new LinkedHashMap<>();
            smsArguments.put("receiver", "${contact.phone}");
            smsArguments.put("content", StringUtils.hasText(request.getSmsContent())
                    ? request.getSmsContent()
                    : "日程提醒：" + event.getTitle() + "，时间：" + event.getStartTime());
            ToolActionStep sms = ToolActionStep.of(30, "sms.send", "sms", smsArguments);
            sms.setOnFailure("CONTINUE");
            steps.add(sms);
        }
        if (StringUtils.hasText(request.getEmailReceiver())
                && request.getReminderMinutes() != null
                && request.getReminderMinutes() > 0) {
            Map<String, Object> emailArguments = new LinkedHashMap<>();
            emailArguments.put("user_id", request.getUserId());
            emailArguments.put("event_id", event.getId());
            emailArguments.put("receiver", request.getEmailReceiver());
            emailArguments.put("content", StringUtils.hasText(request.getEmailContent())
                    ? request.getEmailContent()
                    : "日程提醒：" + event.getTitle() + "，时间：" + event.getStartTime());
            emailArguments.put("run_at", event.getStartTime().minusMinutes(request.getReminderMinutes()).toString());
            ToolActionStep email = ToolActionStep.of(40, "email.schedule", "email", emailArguments);
            email.setOnFailure("CONTINUE");
            steps.add(email);
        }
        return steps;
    }

    public List<ToolActionStep> buildBeforeCalendarUpdate(UpdateEventRequest request, CalendarEventVO event) {
        List<ToolActionStep> steps = new ArrayList<>();
        if (!Boolean.TRUE.equals(request.getOnlineMeeting()) && !hasMeetingCreate(request.getPlannedToolSteps())) {
            return steps;
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("title", StringUtils.hasText(request.getTitle()) ? request.getTitle() : event.getTitle());
        arguments.put("start_time", request.getStartTime() == null ? event.getStartTime() : request.getStartTime());
        arguments.put("end_time", request.getEndTime() == null ? event.getEndTime() : request.getEndTime());
        steps.add(ToolActionStep.of(10, "meeting.create", "meeting", arguments));
        return steps;
    }

    private boolean hasMeetingCreate(List<ToolActionStep> steps) {
        if (steps == null) {
            return false;
        }
        return steps.stream().anyMatch(step -> "meeting.create".equals(step.getSkillId()));
    }
}
