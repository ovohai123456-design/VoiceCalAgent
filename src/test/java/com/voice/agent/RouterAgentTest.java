package com.voice.agent;

import com.voice.agent.agent.AgentConstants;
import com.voice.agent.agent.AgentPlan;
import com.voice.agent.agent.DefaultValueResolver;
import com.voice.agent.agent.LlmRouterAgent;
import com.voice.agent.agent.RouterAgent;
import com.voice.agent.model.dto.AgentExecuteRequest;
import com.voice.agent.model.dto.EventResolveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouterAgentTest {
    @Mock
    private DefaultValueResolver defaultValueResolver;
    @Mock
    private LlmRouterAgent llmRouterAgent;

    private RouterAgent routerAgent;

    @BeforeEach
    void setUp() {
        routerAgent = new RouterAgent(defaultValueResolver, llmRouterAgent);
        lenient().when(defaultValueResolver.resolveUserId(1L)).thenReturn(1L);
        lenient().when(llmRouterAgent.route(any())).thenThrow(new IllegalStateException("disabled"));
    }

    @Test
    void shouldBuildUpdatePlanWithTargetAndNewTime() {
        when(defaultValueResolver.resolveEndTime(any(LocalDateTime.class), any())).thenAnswer(invocation ->
                ((LocalDateTime) invocation.getArgument(0)).plusHours(1)
        );
        AgentPlan plan = routerAgent.route(request("把明天下午三点的项目会改到四点"));

        assertEquals(AgentConstants.INTENT_UPDATE_EVENT, plan.getIntent());
        assertEquals("项目会", plan.getEventResolveRequest().getTitleKeyword());
        assertEquals(LocalDateTime.of(2026, 5, 31, 15, 0), plan.getEventResolveRequest().getRangeStart());
        assertEquals(LocalDateTime.of(2026, 5, 31, 16, 0), plan.getUpdateEventRequest().getStartTime());
        assertEquals(LocalDateTime.of(2026, 5, 31, 17, 0), plan.getUpdateEventRequest().getEndTime());
    }

    @Test
    void shouldBuildDeletePlanBeforeTreatingCalendarWordAsQuery() {
        AgentPlan plan = routerAgent.route(request("取消明天下午三点的项目会日程"));

        assertEquals(AgentConstants.INTENT_DELETE_EVENT, plan.getIntent());
        assertEquals("项目会", plan.getEventResolveRequest().getTitleKeyword());
        assertEquals(LocalDateTime.of(2026, 5, 31, 15, 0), plan.getEventResolveRequest().getRangeStart());
    }

    @Test
    void shouldResolveDeleteByDayWithoutRequiringTitle() {
        AgentPlan plan = routerAgent.route(request("帮我删除今天的日程"));

        assertEquals(AgentConstants.INTENT_DELETE_EVENT, plan.getIntent());
        assertTrue(plan.getEventResolveRequest().getTitleKeyword().isEmpty());
        assertEquals(LocalDateTime.of(2026, 5, 30, 0, 0), plan.getEventResolveRequest().getRangeStart());
        assertEquals(LocalDateTime.of(2026, 5, 31, 0, 0), plan.getEventResolveRequest().getRangeEnd());
        assertFalse(plan.getMissingFields().contains("title"));
    }

    @Test
    void shouldResolveDeleteByDayOfMonthWithoutRequiringTitle() {
        AgentPlan plan = routerAgent.route(request("帮我删除30号的日程"));

        assertEquals(AgentConstants.INTENT_DELETE_EVENT, plan.getIntent());
        assertTrue(plan.getEventResolveRequest().getTitleKeyword().isEmpty());
        assertEquals(LocalDateTime.of(2026, 5, 30, 0, 0), plan.getEventResolveRequest().getRangeStart());
        assertEquals(LocalDateTime.of(2026, 5, 31, 0, 0), plan.getEventResolveRequest().getRangeEnd());
        assertFalse(plan.getMissingFields().contains("title"));
    }

    @Test
    void shouldOverrideLlmCreateIntentForExplicitDeleteCommand() {
        AgentPlan incorrectLlmPlan = new AgentPlan();
        incorrectLlmPlan.setIntent(AgentConstants.INTENT_CREATE_EVENT);
        incorrectLlmPlan.getMissingFields().add("title");
        doReturn(incorrectLlmPlan).when(llmRouterAgent).route(any());

        AgentPlan plan = routerAgent.route(request("帮我删除30号的日程"));

        assertEquals(AgentConstants.INTENT_DELETE_EVENT, plan.getIntent());
        assertEquals(LocalDateTime.of(2026, 5, 30, 0, 0), plan.getEventResolveRequest().getRangeStart());
        assertFalse(plan.getMissingFields().contains("title"));
    }

    @Test
    void shouldEnrichMissingDeleteRangeForDayOfMonth() {
        AgentPlan incompleteLlmPlan = new AgentPlan();
        incompleteLlmPlan.setIntent(AgentConstants.INTENT_DELETE_EVENT);
        incompleteLlmPlan.setEventResolveRequest(new EventResolveRequest());
        incompleteLlmPlan.getMissingFields().add("title");
        doReturn(incompleteLlmPlan).when(llmRouterAgent).route(any());

        AgentPlan plan = routerAgent.route(request("帮我删除30号的日程"));

        assertEquals(LocalDateTime.of(2026, 5, 30, 0, 0), plan.getEventResolveRequest().getRangeStart());
        assertEquals(LocalDateTime.of(2026, 5, 31, 0, 0), plan.getEventResolveRequest().getRangeEnd());
        assertFalse(plan.getMissingFields().contains("title"));
    }

    @Test
    void shouldRequireTargetWhenDeleteHasNeitherTitleNorTime() {
        AgentPlan plan = routerAgent.route(request("帮我删除日程"));

        assertEquals(AgentConstants.INTENT_DELETE_EVENT, plan.getIntent());
        assertTrue(plan.getMissingFields().contains("title"));
        assertNull(plan.getEventResolveRequest().getRangeStart());
        assertNull(plan.getEventResolveRequest().getRangeEnd());
    }

    @Test
    void shouldNotConstrainTitleOnlyDeleteToToday() {
        AgentPlan plan = routerAgent.route(request("帮我删除项目会"));

        assertEquals(AgentConstants.INTENT_DELETE_EVENT, plan.getIntent());
        assertEquals("项目会", plan.getEventResolveRequest().getTitleKeyword());
        assertNull(plan.getEventResolveRequest().getRangeStart());
        assertNull(plan.getEventResolveRequest().getRangeEnd());
        assertFalse(plan.getMissingFields().contains("title"));
    }

    @Test
    void shouldNotConstrainTitleOnlyUpdateToToday() {
        when(defaultValueResolver.resolveEndTime(any(LocalDateTime.class), any())).thenAnswer(invocation ->
                ((LocalDateTime) invocation.getArgument(0)).plusHours(1)
        );

        AgentPlan plan = routerAgent.route(request("把项目会改到四点"));

        assertEquals(AgentConstants.INTENT_UPDATE_EVENT, plan.getIntent());
        assertEquals("项目会", plan.getEventResolveRequest().getTitleKeyword());
        assertNull(plan.getEventResolveRequest().getRangeStart());
        assertNull(plan.getEventResolveRequest().getRangeEnd());
        assertFalse(plan.getMissingFields().contains("title"));
    }

    @Test
    void fallbackCreateShouldKeepMeetingAndSmsExtensions() {
        AgentPlan plan = routerAgent.route(request(
                "\u660e\u5929\u4e0b\u5348\u4e09\u70b9\u5b89\u6392\u7ebf\u4e0a\u9879\u76ee\u4f1a\uff0c\u77ed\u4fe1\u63d0\u9192\u5f20\u4e09"
        ));

        assertEquals(AgentConstants.INTENT_CREATE_EVENT, plan.getIntent());
        assertTrue(plan.getCreateEventRequest().getOnlineMeeting());
        assertEquals("\u5f20\u4e09", plan.getCreateEventRequest().getSmsReceiver());
    }

    private AgentExecuteRequest request(String text) {
        AgentExecuteRequest request = new AgentExecuteRequest();
        request.setUserId(1L);
        request.setText(text);
        request.setCurrentTime("2026-05-30 10:00:00");
        return request;
    }
}
