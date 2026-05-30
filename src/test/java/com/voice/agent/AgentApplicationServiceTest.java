package com.voice.agent;

import com.voice.agent.agent.AgentApplicationService;
import com.voice.agent.agent.ActionPlanBuilder;
import com.voice.agent.agent.AgentConstants;
import com.voice.agent.agent.AgentPlan;
import com.voice.agent.agent.CalendarAgent;
import com.voice.agent.agent.CommandActionService;
import com.voice.agent.agent.CommandWorkflowService;
import com.voice.agent.agent.ConfirmService;
import com.voice.agent.agent.DefaultValueResolver;
import com.voice.agent.agent.RouterAgent;
import com.voice.agent.model.dto.AgentConfirmRequest;
import com.voice.agent.model.dto.AgentExecuteRequest;
import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.entity.CommandActionEntity;
import com.voice.agent.model.entity.CommandTaskEntity;
import com.voice.agent.model.entity.ExecutionLogEntity;
import com.voice.agent.model.entity.PendingConfirmationEntity;
import com.voice.agent.model.vo.AgentResponse;
import com.voice.agent.model.vo.CalendarEventVO;
import com.voice.agent.model.vo.ConflictResultVO;
import com.voice.agent.tool.GenericToolAgent;
import com.voice.agent.tool.ToolActionStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentApplicationServiceTest {
    @Mock
    private RouterAgent routerAgent;
    @Mock
    private CalendarAgent calendarAgent;
    @Mock
    private CommandActionService commandActionService;
    @Mock
    private ConfirmService confirmService;
    @Mock
    private CommandWorkflowService commandWorkflowService;
    @Mock
    private DefaultValueResolver defaultValueResolver;
    @Mock
    private ActionPlanBuilder actionPlanBuilder;
    @Mock
    private GenericToolAgent genericToolAgent;

    private AgentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AgentApplicationService(
                routerAgent,
                calendarAgent,
                commandActionService,
                confirmService,
                commandWorkflowService,
                defaultValueResolver,
                actionPlanBuilder,
                genericToolAgent
        );
    }

    @Test
    void createEventShouldPersistActionBeforePendingConfirmation() {
        AgentExecuteRequest request = executeRequest();
        CommandTaskEntity task = task();
        AgentPlan plan = createPlan();
        CalendarAgent.PreparedCreateAction prepared = preparedCreateAction(plan.getCreateEventRequest());
        CommandActionEntity action = action();
        PendingConfirmationEntity confirmation = confirmation();

        when(defaultValueResolver.resolveUserId(1L)).thenReturn(1L);
        when(commandWorkflowService.createTask(request)).thenReturn(task);
        when(commandWorkflowService.addLog(eq("task_001"), eq(1), any(), any(), any(), any()))
                .thenReturn(new ExecutionLogEntity());
        when(routerAgent.route(request)).thenReturn(plan);
        when(commandWorkflowService.addLog(eq("task_001"), eq(2), any(), any(), any(), any()))
                .thenReturn(new ExecutionLogEntity());
        when(calendarAgent.prepareCreateAction(plan.getCreateEventRequest(), "task_001")).thenReturn(prepared);
        when(commandActionService.createAction("task_001", AgentConstants.ACTION_CREATE_EVENT, plan.getCreateEventRequest()))
                .thenReturn(action);
        when(confirmService.createPendingConfirmation(1L, "session_001", "action_001"))
                .thenReturn(confirmation);
        when(commandWorkflowService.addLog(eq("task_001"), eq(3), any(), any(), any(), any()))
                .thenReturn(new ExecutionLogEntity());

        AgentResponse response = service.execute(request);

        assertTrue(response.getSuccess());
        assertTrue(response.getNeedConfirm());
        assertEquals("ct_001", response.getConfirmToken());
        assertEquals("task_001", response.getTaskId());

        InOrder order = inOrder(commandActionService, confirmService);
        order.verify(commandActionService)
                .createAction("task_001", AgentConstants.ACTION_CREATE_EVENT, plan.getCreateEventRequest());
        order.verify(commandActionService).markWaitingConfirm(action);
        order.verify(confirmService).createPendingConfirmation(1L, "session_001", "action_001");
    }

    @Test
    void confirmShouldResolveActionAndCreateCalendarEvent() {
        AgentConfirmRequest request = new AgentConfirmRequest();
        request.setUserId(1L);
        request.setSessionId("session_001");
        request.setConfirmToken("ct_001");

        PendingConfirmationEntity confirmation = confirmation();
        CommandActionEntity action = action();
        CreateEventRequest createEventRequest = createEventRequest();
        CalendarEventVO event = calendarEvent();
        ExecutionLogEntity executeLog = new ExecutionLogEntity();

        when(defaultValueResolver.resolveUserId(1L)).thenReturn(1L);
        when(confirmService.getPendingConfirmation(1L, "session_001", "ct_001")).thenReturn(confirmation);
        when(commandActionService.getAction("action_001")).thenReturn(action);
        when(commandWorkflowService.addLog(eq("task_001"), eq(4), any(), any(), any(), any()))
                .thenReturn(executeLog);
        when(commandActionService.readPayload(action, CreateEventRequest.class)).thenReturn(createEventRequest);
        when(calendarAgent.executeCreate(createEventRequest)).thenReturn(event);
        when(calendarAgent.buildCreateSuccessText(event)).thenReturn("created");

        AgentResponse response = service.confirm(request);

        assertTrue(response.getSuccess());
        assertFalse(response.getNeedConfirm());
        assertEquals("task_001", response.getTaskId());
        assertEquals(event, response.getData());

        InOrder order = inOrder(confirmService, commandActionService, calendarAgent, commandWorkflowService);
        order.verify(confirmService).getPendingConfirmation(1L, "session_001", "ct_001");
        order.verify(commandActionService).getAction("action_001");
        order.verify(commandActionService).readPayload(action, CreateEventRequest.class);
        order.verify(calendarAgent).executeCreate(createEventRequest);
        order.verify(confirmService).markConfirmed(confirmation);
        order.verify(commandActionService).markExecuted(action);
        order.verify(commandWorkflowService).finishSuccess(
                "task_001",
                AgentConstants.INTENT_CREATE_EVENT,
                "created",
                "created"
        );
    }

    @Test
    void executeShouldMergeRecentClarificationContext() {
        AgentExecuteRequest request = executeRequest();
        request.setText("明天下午三点");
        CommandTaskEntity previous = new CommandTaskEntity();
        previous.setTaskId("task_previous");
        previous.setInputText("帮我安排项目会");
        CommandTaskEntity task = task();
        AgentPlan plan = new AgentPlan();
        plan.setIntent(AgentConstants.INTENT_UNKNOWN);
        plan.getMissingFields().add("intent");

        when(defaultValueResolver.resolveUserId(1L)).thenReturn(1L);
        when(commandWorkflowService.findLatestNeedClarification(1L, "session_001", 10)).thenReturn(previous);
        when(commandWorkflowService.createTask(request)).thenReturn(task);
        when(commandWorkflowService.addLog(eq("task_001"), eq(1), any(), any(), any(), any()))
                .thenReturn(new ExecutionLogEntity());
        when(routerAgent.route(request)).thenReturn(plan);

        service.execute(request);

        assertEquals("帮我安排项目会 明天下午三点", request.getText());
        verify(commandWorkflowService).markClarificationContinued("task_previous");
    }

    @Test
    void confirmShouldCreateMeetingBeforeCalendarAndSendSmsAfterCalendar() {
        AgentConfirmRequest request = new AgentConfirmRequest();
        request.setUserId(1L);
        request.setSessionId("session_001");
        request.setConfirmToken("ct_001");

        PendingConfirmationEntity confirmation = confirmation();
        CommandActionEntity action = action();
        CreateEventRequest createEventRequest = createEventRequest();
        createEventRequest.setOnlineMeeting(true);
        createEventRequest.setSmsReceiver("Zhang San");
        CalendarEventVO event = calendarEvent();
        ToolActionStep meetingStep = ToolActionStep.of(10, "meeting.create", "meeting", Collections.emptyMap());
        ToolActionStep smsStep = ToolActionStep.of(30, "sms.send", "sms", Collections.emptyMap());
        Map<String, Object> meetingData = new LinkedHashMap<>();
        meetingData.put("meeting_url", "https://meeting.voicecal.local/001");
        GenericToolAgent.ToolExecutionSummary meetingSummary = new GenericToolAgent.ToolExecutionSummary();
        meetingSummary.setContext(Collections.singletonMap("meeting", meetingData));

        when(defaultValueResolver.resolveUserId(1L)).thenReturn(1L);
        when(confirmService.getPendingConfirmation(1L, "session_001", "ct_001")).thenReturn(confirmation);
        when(commandActionService.getAction("action_001")).thenReturn(action);
        when(commandWorkflowService.addLog(eq("task_001"), eq(4), any(), any(), any(), any()))
                .thenReturn(new ExecutionLogEntity());
        when(commandActionService.readPayload(action, CreateEventRequest.class)).thenReturn(createEventRequest);
        when(actionPlanBuilder.buildBeforeCalendarCreate(createEventRequest))
                .thenReturn(Collections.singletonList(meetingStep));
        when(genericToolAgent.execute("task_001", Collections.singletonList(meetingStep))).thenReturn(meetingSummary);
        when(calendarAgent.executeCreate(createEventRequest)).thenReturn(event);
        when(actionPlanBuilder.buildAfterCalendarCreate(createEventRequest, event))
                .thenReturn(Collections.singletonList(smsStep));
        when(genericToolAgent.execute("task_001", Collections.singletonList(smsStep)))
                .thenReturn(new GenericToolAgent.ToolExecutionSummary());
        when(calendarAgent.buildCreateSuccessText(event)).thenReturn("created");

        AgentResponse response = service.confirm(request);

        assertTrue(response.getSuccess());
        assertEquals("https://meeting.voicecal.local/001", createEventRequest.getMeetingUrl());
        InOrder order = inOrder(genericToolAgent, calendarAgent);
        order.verify(genericToolAgent).execute("task_001", Collections.singletonList(meetingStep));
        order.verify(calendarAgent).executeCreate(createEventRequest);
        order.verify(genericToolAgent).execute("task_001", Collections.singletonList(smsStep));
    }

    private AgentExecuteRequest executeRequest() {
        AgentExecuteRequest request = new AgentExecuteRequest();
        request.setUserId(1L);
        request.setSessionId("session_001");
        request.setInputType("TEXT");
        request.setText("tomorrow 3pm project meeting");
        request.setTimezone("Asia/Shanghai");
        request.setCurrentTime("2026-05-30 10:00:00");
        return request;
    }

    private CommandTaskEntity task() {
        CommandTaskEntity task = new CommandTaskEntity();
        task.setTaskId("task_001");
        task.setUserId(1L);
        task.setSessionId("session_001");
        return task;
    }

    private AgentPlan createPlan() {
        AgentPlan plan = new AgentPlan();
        plan.setIntent(AgentConstants.INTENT_CREATE_EVENT);
        plan.setActionType(AgentConstants.ACTION_CREATE_EVENT);
        plan.setNeedConfirm(true);
        plan.setCreateEventRequest(createEventRequest());
        return plan;
    }

    private CreateEventRequest createEventRequest() {
        CreateEventRequest request = new CreateEventRequest();
        request.setUserId(1L);
        request.setTitle("project meeting");
        request.setStartTime(LocalDateTime.of(2026, 5, 31, 15, 0));
        request.setEndTime(LocalDateTime.of(2026, 5, 31, 16, 0));
        return request;
    }

    private CalendarAgent.PreparedCreateAction preparedCreateAction(CreateEventRequest request) {
        CalendarAgent.PreparedCreateAction prepared = new CalendarAgent.PreparedCreateAction();
        prepared.setCreateEventRequest(request);
        prepared.setConflictResult(new ConflictResultVO());
        prepared.setRequiresSlotSelection(false);
        prepared.setReplyText("confirm");
        return prepared;
    }

    private CommandActionEntity action() {
        CommandActionEntity action = new CommandActionEntity();
        action.setActionId("action_001");
        action.setTaskId("task_001");
        action.setActionType(AgentConstants.ACTION_CREATE_EVENT);
        return action;
    }

    private PendingConfirmationEntity confirmation() {
        PendingConfirmationEntity confirmation = new PendingConfirmationEntity();
        confirmation.setActionId("action_001");
        confirmation.setConfirmToken("ct_001");
        return confirmation;
    }

    private CalendarEventVO calendarEvent() {
        CalendarEventVO event = new CalendarEventVO();
        event.setId(100L);
        event.setTitle("project meeting");
        event.setStartTime(LocalDateTime.of(2026, 5, 31, 15, 0));
        event.setEndTime(LocalDateTime.of(2026, 5, 31, 16, 0));
        return event;
    }
}
