package com.voice.agent;

import com.voice.agent.agent.ActionPlanBuilder;
import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.vo.CalendarEventVO;
import com.voice.agent.tool.ToolActionStep;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionPlanBuilderTest {
    @Test
    void createPlanShouldPlaceMeetingBeforeCalendarAndSmsAfterCalendar() {
        CreateEventRequest request = new CreateEventRequest();
        request.setTitle("project meeting");
        request.setStartTime(LocalDateTime.of(2026, 6, 1, 15, 0));
        request.setEndTime(LocalDateTime.of(2026, 6, 1, 16, 0));
        request.setOnlineMeeting(true);
        request.setSmsReceiver("Zhang San");
        request.setEmailReceiver("demo@example.com");
        request.setReminderMinutes(10);
        CalendarEventVO event = new CalendarEventVO();
        event.setId(100L);
        event.setTitle("project meeting");
        event.setStartTime(request.getStartTime());

        ActionPlanBuilder builder = new ActionPlanBuilder();
        List<ToolActionStep> before = builder.buildBeforeCalendarCreate(request);
        List<ToolActionStep> after = builder.buildAfterCalendarCreate(request, event);

        assertEquals("meeting.create", before.get(0).getSkillId());
        assertEquals(10, before.get(0).getStepOrder());
        assertEquals("contact.query", after.get(0).getSkillId());
        assertEquals("sms.send", after.get(1).getSkillId());
        assertEquals("CONTINUE", after.get(1).getOnFailure());
        assertEquals(2, after.size());
    }

    @Test
    void createPlanShouldIgnoreModelPlannedMeetingWithoutExplicitMeetingIntent() {
        CreateEventRequest request = new CreateEventRequest();
        request.setTitle("买鸡蛋");
        request.setStartTime(LocalDateTime.of(2026, 6, 4, 15, 0));
        request.setEndTime(LocalDateTime.of(2026, 6, 4, 16, 0));
        request.setOnlineMeeting(false);
        request.setPlannedToolSteps(Collections.singletonList(
                ToolActionStep.of(10, "meeting.create", "meeting", Collections.emptyMap())
        ));

        assertTrue(new ActionPlanBuilder().buildBeforeCalendarCreate(request).isEmpty());
    }
}
