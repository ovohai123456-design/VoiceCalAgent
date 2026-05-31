package com.voice.agent;

import com.voice.agent.agent.CalendarAgent;
import com.voice.agent.agent.EventResolveService;
import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.dto.UpdateEventRequest;
import com.voice.agent.model.vo.CalendarEventVO;
import com.voice.agent.model.vo.ConflictResultVO;
import com.voice.agent.model.vo.FreeSlotVO;
import com.voice.agent.service.CalendarService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CalendarAgentTest {
    private final CalendarService calendarService = mock(CalendarService.class);
    private final EventResolveService eventResolveService = mock(EventResolveService.class);
    private final CalendarAgent calendarAgent = new CalendarAgent(calendarService, eventResolveService);

    @Test
    void conflictShouldReturnCandidatesWithoutSilentlyChangingRequestedTime() {
        CreateEventRequest request = new CreateEventRequest();
        request.setUserId(1L);
        request.setTitle("项目会");
        request.setStartTime(LocalDateTime.of(2026, 5, 31, 15, 0));
        request.setEndTime(LocalDateTime.of(2026, 5, 31, 16, 0));

        FreeSlotVO suggested = new FreeSlotVO();
        suggested.setStartTime(LocalDateTime.of(2026, 5, 31, 16, 0));
        suggested.setEndTime(LocalDateTime.of(2026, 5, 31, 17, 0));
        ConflictResultVO conflict = new ConflictResultVO();
        conflict.setHasConflict(true);
        conflict.setSuggestedSlots(Collections.singletonList(suggested));
        when(calendarService.checkConflict(any())).thenReturn(conflict);

        CalendarAgent.PreparedCreateAction prepared = calendarAgent.prepareCreateAction(request, "task_001");

        assertTrue(prepared.getRequiresSlotSelection());
        assertEquals(LocalDateTime.of(2026, 5, 31, 15, 0), prepared.getCreateEventRequest().getStartTime());
        assertEquals(LocalDateTime.of(2026, 5, 31, 16, 0), prepared.getConflictResult().getSuggestedSlots().get(0).getStartTime());
    }

    @Test
    void createAndQueryRepliesShouldIncludeTencentMeetingDetails() {
        com.voice.agent.model.vo.CalendarEventVO event = new com.voice.agent.model.vo.CalendarEventVO();
        event.setTitle("腾讯会议");
        event.setStartTime(LocalDateTime.of(2026, 5, 31, 15, 0));
        event.setEndTime(LocalDateTime.of(2026, 5, 31, 16, 0));
        event.setMeetingProvider("TENCENT_MEETING");
        event.setMeetingCode("123456789");
        event.setMeetingUrl("https://meeting.voicecal.local/tencent/123456789");

        String created = calendarAgent.buildCreateSuccessText(event);
        String queried = calendarAgent.buildQueryReply(Arrays.asList(event));

        assertTrue(created.contains("腾讯会议，会议号 123456789"));
        assertTrue(created.contains("https://meeting.voicecal.local/tencent/123456789"));
        assertTrue(queried.contains("腾讯会议，会议号 123456789"));
    }

    @Test
    void propertyOnlyUpdateShouldKeepOriginalTime() {
        CalendarEventVO event = new CalendarEventVO();
        event.setId(100L);
        event.setUserId(1L);
        event.setTitle("会议");
        event.setStartTime(LocalDateTime.of(2026, 6, 1, 10, 0));
        event.setEndTime(LocalDateTime.of(2026, 6, 1, 12, 0));
        UpdateEventRequest request = new UpdateEventRequest();
        request.setOnlineMeeting(true);
        ConflictResultVO conflict = new ConflictResultVO();
        conflict.setHasConflict(false);
        when(calendarService.checkConflict(any())).thenReturn(conflict);

        CalendarAgent.PreparedUpdateAction prepared = calendarAgent.prepareUpdateAction(event, request, "task_001");

        assertTrue(prepared.getReplyText().contains("2026-06-01 10:00 到 2026-06-01 12:00"));
        assertTrue(prepared.getReplyText().contains("创建腾讯会议号和入会链接"));
    }
}
