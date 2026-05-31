package com.voice.agent;

import com.voice.agent.agent.AgentApplicationService;
import com.voice.agent.agent.ActionPlanBuilder;
import com.voice.agent.agent.AgentConstants;
import com.voice.agent.agent.AgentPlan;
import com.voice.agent.agent.CalendarAgent;
import com.voice.agent.agent.ChatAgent;
import com.voice.agent.agent.CommandActionService;
import com.voice.agent.agent.CommandWorkflowService;
import com.voice.agent.agent.ConfirmService;
import com.voice.agent.agent.ConversationConstants;
import com.voice.agent.agent.ConversationMemoryService;
import com.voice.agent.agent.DefaultValueResolver;
import com.voice.agent.agent.RouterAgent;
import com.voice.agent.model.dto.AgentConfirmRequest;
import com.voice.agent.model.dto.AgentExecuteRequest;
import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.dto.EventResolveRequest;
import com.voice.agent.model.entity.CommandActionEntity;
import com.voice.agent.model.entity.CommandTaskEntity;
import com.voice.agent.model.entity.ConversationStateEntity;
import com.voice.agent.model.entity.ExecutionLogEntity;
import com.voice.agent.model.entity.PendingConfirmationEntity;
import com.voice.agent.model.vo.AgentResponse;
import com.voice.agent.model.vo.CalendarEventVO;
import com.voice.agent.model.vo.ConflictResultVO;
import com.voice.agent.tool.GenericToolAgent;
import com.voice.agent.tool.ToolActionStep;
import com.voice.agent.tool.ToolResultReplyFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentApplicationServiceTest {
    @Mock
    private RouterAgent routerAgent;
    @Mock
    private ChatAgent chatAgent;
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
    @Mock
    private ToolResultReplyFormatter toolResultReplyFormatter;
    @Mock
    private ConversationMemoryService conversationMemoryService;

    private AgentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AgentApplicationService(
                routerAgent,
                chatAgent,
                calendarAgent,
                commandActionService,
                confirmService,
                commandWorkflowService,
                defaultValueResolver,
                actionPlanBuilder,
                genericToolAgent,
                toolResultReplyFormatter,
                conversationMemoryService
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
        ConversationStateEntity activeState = clarificationState();
        CommandTaskEntity previous = new CommandTaskEntity();
        previous.setTaskId("task_previous");
        previous.setInputText("帮我安排项目会");
        CommandTaskEntity task = task();
        AgentPlan plan = new AgentPlan();
        plan.setIntent(AgentConstants.INTENT_UNKNOWN);
        plan.getMissingFields().add("intent");

        when(defaultValueResolver.resolveUserId(1L)).thenReturn(1L);
        when(conversationMemoryService.latestActiveState(1L, "session_001")).thenReturn(activeState);
        when(commandWorkflowService.findTask("task_previous")).thenReturn(previous);
        when(commandWorkflowService.createTask(request)).thenReturn(task);
        when(commandWorkflowService.addLog(eq("task_001"), eq(1), any(), any(), any(), any()))
                .thenReturn(new ExecutionLogEntity());
        when(routerAgent.route(request)).thenReturn(plan);

        service.execute(request);

        assertEquals("帮我安排项目会 明天下午三点", request.getText());
        verify(commandWorkflowService).markClarificationContinued("task_previous");
    }

    @Test
    void chatShouldReturnNaturalReplyAndFinishTask() {
        AgentExecuteRequest request = executeRequest();
        request.setText("你好");
        CommandTaskEntity task = task();
        AgentPlan plan = new AgentPlan();
        plan.setIntent(AgentConstants.INTENT_CHAT);
        ExecutionLogEntity routeLog = new ExecutionLogEntity();
        ExecutionLogEntity chatLog = new ExecutionLogEntity();

        when(defaultValueResolver.resolveUserId(1L)).thenReturn(1L);
        when(commandWorkflowService.createTask(request)).thenReturn(task);
        when(commandWorkflowService.addLog(eq("task_001"), eq(1), any(), any(), any(), any()))
                .thenReturn(routeLog);
        when(routerAgent.route(request)).thenReturn(plan);
        when(commandWorkflowService.addLog(eq("task_001"), eq(2), any(), any(), any(), any()))
                .thenReturn(chatLog);
        when(chatAgent.reply("你好", null)).thenReturn("你好，我是 VoiceCal。");

        AgentResponse response = service.execute(request);

        assertTrue(response.getSuccess());
        assertEquals("你好，我是 VoiceCal。", response.getReplyText());
        verify(commandWorkflowService).finishSuccess(
                "task_001",
                AgentConstants.INTENT_CHAT,
                "你好，我是 VoiceCal。",
                "你好，我是 VoiceCal。"
        );
    }

    @Test
    void executeShouldUseSupplementalWeatherCityWithoutUnrelatedClarificationText() {
        AgentExecuteRequest request = executeRequest();
        request.setText("合肥");
        ConversationStateEntity activeState = clarificationState();
        CommandTaskEntity previous = new CommandTaskEntity();
        previous.setTaskId("task_previous");
        previous.setInputText("之前我问你了那个城市");
        CommandTaskEntity task = task();
        AgentPlan clarificationPlan = new AgentPlan();
        clarificationPlan.getMissingFields().add("location");
        AgentPlan routedPlan = new AgentPlan();
        routedPlan.setIntent(AgentConstants.INTENT_UNKNOWN);
        routedPlan.getMissingFields().add("intent");

        when(defaultValueResolver.resolveUserId(1L)).thenReturn(1L);
        when(conversationMemoryService.latestActiveState(1L, "session_001")).thenReturn(activeState);
        when(conversationMemoryService.readStateContext(activeState, AgentPlan.class)).thenReturn(clarificationPlan);
        when(commandWorkflowService.findTask("task_previous")).thenReturn(previous);
        when(commandWorkflowService.createTask(request)).thenReturn(task);
        when(commandWorkflowService.addLog(eq("task_001"), eq(1), any(), any(), any(), any()))
                .thenReturn(new ExecutionLogEntity());
        when(routerAgent.route(request)).thenReturn(routedPlan);

        service.execute(request);

        assertEquals("查询天气 合肥", request.getText());
        verify(commandWorkflowService).markClarificationContinued("task_previous");
    }

    @Test
    void executeShouldReplaceClarificationStateWhenDeleteCommandStarts() {
        AgentExecuteRequest request = executeRequest();
        request.setText("帮我删除今天的日程");
        ConversationStateEntity activeState = clarificationState();
        CommandTaskEntity task = task();
        AgentPlan plan = new AgentPlan();
        plan.setIntent(AgentConstants.INTENT_UNKNOWN);
        plan.getMissingFields().add("intent");

        when(defaultValueResolver.resolveUserId(1L)).thenReturn(1L);
        when(conversationMemoryService.latestActiveState(1L, "session_001")).thenReturn(activeState);
        when(commandWorkflowService.createTask(request)).thenReturn(task);
        when(commandWorkflowService.addLog(eq("task_001"), eq(1), any(), any(), any(), any()))
                .thenReturn(new ExecutionLogEntity());
        when(routerAgent.route(request)).thenReturn(plan);

        service.execute(request);

        assertEquals("帮我删除今天的日程", request.getText());
        verify(commandWorkflowService).cancelTask("task_previous", "已被新的指令替代。");
        verify(conversationMemoryService).closeState(activeState);
        verify(commandWorkflowService, never()).markClarificationContinued("task_previous");
    }

    @Test
    void deleteClarificationShouldAskWhichEventToDelete() {
        AgentExecuteRequest request = executeRequest();
        request.setText("删除日程");
        CommandTaskEntity task = task();
        AgentPlan plan = new AgentPlan();
        plan.setIntent(AgentConstants.INTENT_DELETE_EVENT);
        plan.getMissingFields().add("title");

        when(defaultValueResolver.resolveUserId(1L)).thenReturn(1L);
        when(commandWorkflowService.createTask(request)).thenReturn(task);
        when(commandWorkflowService.addLog(eq("task_001"), eq(1), any(), any(), any(), any()))
                .thenReturn(new ExecutionLogEntity());
        when(routerAgent.route(request)).thenReturn(plan);

        AgentResponse response = service.execute(request);

        assertEquals("你想删除哪个日程？", response.getReplyText());
    }

    @Test
    void executeShouldCancelPendingActionWhenNewTextDoesNotConfirmIt() {
        AgentExecuteRequest request = executeRequest();
        request.setText("天气怎么样");
        ConversationStateEntity activeState = confirmationState();
        PendingConfirmationEntity confirmation = confirmation();
        CommandActionEntity action = action();
        action.setTaskId("task_previous");
        CommandTaskEntity task = task();
        AgentPlan plan = new AgentPlan();
        plan.setIntent(AgentConstants.INTENT_UNKNOWN);
        plan.getMissingFields().add("intent");

        when(defaultValueResolver.resolveUserId(1L)).thenReturn(1L);
        when(conversationMemoryService.latestActiveState(1L, "session_001")).thenReturn(activeState);
        when(confirmService.getPendingConfirmation(1L, "session_001", "ct_001")).thenReturn(confirmation);
        when(commandActionService.getAction("action_001")).thenReturn(action);
        when(commandWorkflowService.createTask(request)).thenReturn(task);
        when(commandWorkflowService.addLog(eq("task_001"), eq(1), any(), any(), any(), any()))
                .thenReturn(new ExecutionLogEntity());
        when(routerAgent.route(request)).thenReturn(plan);

        service.execute(request);

        verify(confirmService).markCanceled(confirmation);
        verify(commandActionService).markCanceled(action);
        verify(commandWorkflowService).cancelTask("task_previous", "已取消该操作。");
        verify(conversationMemoryService).closeState(activeState);
    }

    @Test
    void shouldParseNaturalLanguageCandidateIndexBeyondThirdOption() {
        assertEquals(Integer.valueOf(3), ReflectionTestUtils.invokeMethod(service, "parseSelectionIndex", "我选第四个"));
        assertEquals(Integer.valueOf(11), ReflectionTestUtils.invokeMethod(service, "parseSelectionIndex", "第十二个"));
        assertEquals(Integer.valueOf(4), ReflectionTestUtils.invokeMethod(service, "parseSelectionIndex", "5号"));
    }

    @Test
    void shouldResolveRecentEventReferenceFromConversationContext() {
        AgentExecuteRequest request = executeRequest();
        request.setText("帮我把刚才的会议改成腾讯会议");
        EventResolveRequest resolveRequest = new EventResolveRequest();
        resolveRequest.setReference("LAST_MENTIONED_EVENT");
        AgentPlan plan = new AgentPlan();
        plan.setEventResolveRequest(resolveRequest);
        plan.getMissingFields().add("title");

        when(conversationMemoryService.findLastMentionedEventId(1L, "session_001")).thenReturn(99L);

        ReflectionTestUtils.invokeMethod(service, "enrichPlanConversationReference", request, plan);

        assertEquals(Long.valueOf(99L), resolveRequest.getEventId());
        assertFalse(plan.getMissingFields().contains("title"));
    }

    @Test
    void shouldResolveIndexedEventReferenceFromPreviousQueryResults() {
        AgentExecuteRequest request = executeRequest();
        request.setText("删除明天第一个的会议");
        EventResolveRequest resolveRequest = new EventResolveRequest();
        resolveRequest.setTitleKeyword("会议");
        resolveRequest.setRangeStart(LocalDateTime.of(2026, 5, 31, 21, 0));
        resolveRequest.setRangeEnd(LocalDateTime.of(2026, 5, 31, 22, 0));
        AgentPlan plan = new AgentPlan();
        plan.setEventResolveRequest(resolveRequest);
        plan.getMissingFields().add("title");
        plan.getMissingFields().add("start_time");
        plan.getMissingFields().add("end_time");

        when(conversationMemoryService.findRecentQueryEventId(1L, "session_001", 0)).thenReturn(88L);

        ReflectionTestUtils.invokeMethod(service, "enrichPlanConversationReference", request, plan);

        assertEquals(Long.valueOf(88L), resolveRequest.getEventId());
        assertNull(resolveRequest.getTitleKeyword());
        assertNull(resolveRequest.getRangeStart());
        assertNull(resolveRequest.getRangeEnd());
        assertTrue(plan.getMissingFields().isEmpty());
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
        meetingData.put("meeting_code", "123456789");
        meetingData.put("provider", "TENCENT_MEETING");
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
        assertEquals("123456789", createEventRequest.getMeetingCode());
        assertEquals("TENCENT_MEETING", createEventRequest.getMeetingProvider());
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

    private ConversationStateEntity clarificationState() {
        ConversationStateEntity state = new ConversationStateEntity();
        state.setId(1L);
        state.setTaskId("task_previous");
        state.setStateType(ConversationConstants.STATE_CLARIFY);
        return state;
    }

    private ConversationStateEntity confirmationState() {
        ConversationStateEntity state = new ConversationStateEntity();
        state.setId(2L);
        state.setTaskId("task_previous");
        state.setActionId("action_001");
        state.setConfirmToken("ct_001");
        state.setStateType(ConversationConstants.STATE_CONFIRM);
        return state;
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
