package com.voice.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voice.agent.agent.AgentConstants;
import com.voice.agent.agent.DefaultValueResolver;
import com.voice.agent.agent.LlmRouterAgent;
import com.voice.agent.llm.LlmClient;
import com.voice.agent.llm.LlmJsonExtractor;
import com.voice.agent.llm.PromptTemplateService;
import com.voice.agent.model.dto.AgentExecuteRequest;
import com.voice.agent.skill.SkillDefinition;
import com.voice.agent.skill.SkillRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmRouterAgentTest {
    @Mock
    private LlmClient llmClient;
    @Mock
    private LlmJsonExtractor jsonExtractor;
    @Mock
    private PromptTemplateService promptTemplateService;
    @Mock
    private DefaultValueResolver defaultValueResolver;
    @Mock
    private SkillRegistry skillRegistry;

    private LlmRouterAgent routerAgent;

    @BeforeEach
    void setUp() {
        routerAgent = new LlmRouterAgent(
                llmClient,
                jsonExtractor,
                promptTemplateService,
                new ObjectMapper(),
                defaultValueResolver,
                skillRegistry
        );
        ReflectionTestUtils.setField(routerAgent, "enabled", true);
        when(promptTemplateService.render(anyString(), any())).thenReturn("prompt");
        when(llmClient.chat(anyString(), anyString())).thenReturn("raw");
        when(jsonExtractor.extractObject("raw")).thenReturn(
                "{\"intent\":\"CREATE_EVENT\",\"slots\":{\"title\":\"项目会\"},"
                        + "\"missingFields\":[\"startTime\",\"start_time\",\"\",\"startTime\"]}"
        );
        lenient().when(defaultValueResolver.resolveUserId(1L)).thenReturn(1L);
        when(skillRegistry.list()).thenReturn(Collections.emptyList());
    }

    @Test
    void shouldNormalizeAndDeduplicateMissingFieldAliases() {
        AgentExecuteRequest request = new AgentExecuteRequest();
        request.setUserId(1L);
        request.setText("帮我安排项目会");
        request.setCurrentTime("2026-05-30 18:00:00");

        assertEquals(
                Collections.singletonList("start_time"),
                routerAgent.route(request).getMissingFields()
        );
    }

    @Test
    void shouldCorrectRelativeDateReturnedByLlm() {
        when(jsonExtractor.extractObject("raw")).thenReturn(
                "{\"intent\":\"CREATE_EVENT\",\"slots\":{\"title\":\"sync\","
                        + "\"startTime\":\"2026-05-31 17:00:00\","
                        + "\"endTime\":\"2026-05-31 18:00:00\"}}"
        );
        AgentExecuteRequest request = new AgentExecuteRequest();
        request.setUserId(1L);
        request.setText("\u540e\u5929\u4e0b\u5348\u4e94\u70b9\u5b89\u6392\u63a5\u53e3\u8054\u8c03\u4f1a");
        request.setCurrentTime("2026-05-30 19:00:00");

        assertEquals(
                LocalDateTime.of(2026, 6, 1, 17, 0),
                routerAgent.route(request).getCreateEventRequest().getStartTime()
        );
        assertEquals(
                LocalDateTime.of(2026, 6, 1, 18, 0),
                routerAgent.route(request).getCreateEventRequest().getEndTime()
        );
    }

    @Test
    void shouldUseDefaultDurationWhenRelativeOffsetWasMistakenForDuration() {
        when(jsonExtractor.extractObject("raw")).thenReturn(
                "{\"intent\":\"CREATE_EVENT\",\"slots\":{\"title\":\"腾讯会议\","
                        + "\"startTime\":\"2026-05-31 18:12:00\","
                        + "\"endTime\":\"2026-05-31 18:24:00\",\"onlineMeeting\":true}}"
        );
        AgentExecuteRequest request = new AgentExecuteRequest();
        request.setUserId(1L);
        request.setText("创建一个12分钟之后的腾讯会议");
        request.setCurrentTime("2026-05-31 18:00:00");

        assertEquals(
                LocalDateTime.of(2026, 5, 31, 18, 12),
                routerAgent.route(request).getCreateEventRequest().getStartTime()
        );
        assertNull(routerAgent.route(request).getCreateEventRequest().getEndTime());
    }

    @Test
    void shouldApplyExplicitDurationToRelativeStartTime() {
        when(jsonExtractor.extractObject("raw")).thenReturn(
                "{\"intent\":\"CREATE_EVENT\",\"slots\":{\"title\":\"腾讯会议\","
                        + "\"startTime\":\"2026-05-31 18:12:00\","
                        + "\"endTime\":\"2026-05-31 18:24:00\",\"onlineMeeting\":true}}"
        );
        AgentExecuteRequest request = new AgentExecuteRequest();
        request.setUserId(1L);
        request.setText("创建一个12分钟之后时长为2小时的腾讯会议");
        request.setCurrentTime("2026-05-31 18:00:00");

        assertEquals(
                LocalDateTime.of(2026, 5, 31, 20, 12),
                routerAgent.route(request).getCreateEventRequest().getEndTime()
        );
    }

    @Test
    void shouldRecoverTencentMeetingTitleFromExplicitText() {
        when(jsonExtractor.extractObject("raw")).thenReturn(
                "{\"intent\":\"CREATE_EVENT\",\"slots\":{"
                        + "\"startTime\":\"2026-05-31 18:12:00\","
                        + "\"endTime\":\"2026-05-31 19:12:00\",\"onlineMeeting\":true},"
                        + "\"missingFields\":[\"title\"]}"
        );
        AgentExecuteRequest request = new AgentExecuteRequest();
        request.setUserId(1L);
        request.setText("\u5e2e\u6211\u521b\u5efa\u4e00\u4e2a12\u5206\u949f\u540e\u7684\u817e\u8baf\u4f1a\u8bae \u65f6\u957f\u662f1\u5c0f\u65f6");
        request.setCurrentTime("2026-05-31 18:00:00");

        assertEquals(
                "\u817e\u8baf\u4f1a\u8bae",
                routerAgent.route(request).getCreateEventRequest().getTitle()
        );
        assertFalse(routerAgent.route(request).getMissingFields().contains("title"));
    }

    @Test
    void shouldAllowDeleteByTimeRangeWithoutTitle() {
        when(jsonExtractor.extractObject("raw")).thenReturn(
                "{\"intent\":\"DELETE_EVENT\",\"slots\":{"
                        + "\"targetTitle\":\"日程\","
                        + "\"targetStartTime\":\"2026-05-29 00:00:00\","
                        + "\"targetEndTime\":\"2026-05-30 00:00:00\"},"
                        + "\"missingFields\":[\"title\"]}"
        );
        AgentExecuteRequest request = new AgentExecuteRequest();
        request.setUserId(1L);
        request.setText("帮我删除今天的日程");
        request.setCurrentTime("2026-05-30 19:00:00");

        assertFalse(routerAgent.route(request).getMissingFields().contains("title"));
        assertEquals(
                LocalDateTime.of(2026, 5, 30, 0, 0),
                routerAgent.route(request).getEventResolveRequest().getRangeStart()
        );
        assertEquals(
                LocalDateTime.of(2026, 5, 31, 0, 0),
                routerAgent.route(request).getEventResolveRequest().getRangeEnd()
        );
        assertTrue(routerAgent.route(request).getEventResolveRequest().getDeleteAllMatches());
    }

    @Test
    void shouldBuildGenericSkillPlanFromRegistryCall() {
        SkillDefinition weather = new SkillDefinition();
        weather.setSkillId("weather.query");
        when(skillRegistry.get("weather.query")).thenReturn(weather);
        when(jsonExtractor.extractObject("raw")).thenReturn(
                "{\"intent\":\"RUN_SKILLS\",\"skillCalls\":[{"
                        + "\"stepOrder\":10,\"skillId\":\"weather.query\","
                        + "\"outputAlias\":\"weather\",\"arguments\":{\"location\":\"上海\"}}]}"
        );
        AgentExecuteRequest request = new AgentExecuteRequest();
        request.setUserId(1L);
        request.setText("上海天气怎么样");
        request.setCurrentTime("2026-05-31 12:00:00");

        assertEquals(AgentConstants.INTENT_RUN_SKILLS, routerAgent.route(request).getIntent());
        assertEquals("weather.query", routerAgent.route(request).getToolSteps().get(0).getSkillId());
        assertEquals("上海", routerAgent.route(request).getToolSteps().get(0).getArguments().get("location"));
    }

    @Test
    void shouldBuildChatPlanWithoutMissingFields() {
        when(jsonExtractor.extractObject("raw")).thenReturn(
                "{\"intent\":\"CHAT\",\"targetAgent\":\"CalendarAgent\","
                        + "\"missingFields\":[\"intent\"]}"
        );
        AgentExecuteRequest request = new AgentExecuteRequest();
        request.setUserId(1L);
        request.setText("你好");
        request.setCurrentTime("2026-05-31 12:00:00");

        assertEquals(AgentConstants.INTENT_CHAT, routerAgent.route(request).getIntent());
        assertEquals(AgentConstants.TARGET_CHAT_AGENT, routerAgent.route(request).getTargetAgent());
        assertTrue(routerAgent.route(request).getMissingFields().isEmpty());
    }

    @Test
    void shouldIgnoreModelTimeRangeWhenUserRequestsAllEvents() {
        when(jsonExtractor.extractObject("raw")).thenReturn(
                "{\"intent\":\"QUERY_EVENT\",\"slots\":{"
                        + "\"queryStartTime\":\"2026-05-31 00:00:00\","
                        + "\"queryEndTime\":\"2026-06-01 00:00:00\","
                        + "\"keyword\":\"\u5168\u90e8\"},"
                        + "\"missingFields\":[\"start_time\",\"end_time\"]}"
        );
        AgentExecuteRequest request = new AgentExecuteRequest();
        request.setUserId(1L);
        request.setText("\u67e5\u8be2\u5168\u90e8\u65e5\u7a0b");
        request.setCurrentTime("2026-05-31 12:00:00");

        assertEquals(AgentConstants.INTENT_QUERY_EVENT, routerAgent.route(request).getIntent());
        assertNull(routerAgent.route(request).getQueryEventRequest().getStartTime());
        assertNull(routerAgent.route(request).getQueryEventRequest().getEndTime());
        assertNull(routerAgent.route(request).getQueryEventRequest().getKeyword());
        assertEquals(Collections.emptyList(), routerAgent.route(request).getMissingFields());
    }

    @Test
    void shouldKeepTimeRangeWhenUserRequestsAllEventsOnSpecificDay() {
        when(jsonExtractor.extractObject("raw")).thenReturn(
                "{\"intent\":\"QUERY_EVENT\",\"slots\":{"
                        + "\"queryStartTime\":\"2026-05-31 00:00:00\","
                        + "\"queryEndTime\":\"2026-06-01 00:00:00\"},"
                        + "\"missingFields\":[]}"
        );
        AgentExecuteRequest request = new AgentExecuteRequest();
        request.setUserId(1L);
        request.setText("\u67e5\u8be2\u660e\u5929\u7684\u6240\u6709\u65e5\u7a0b");
        request.setCurrentTime("2026-05-30 12:00:00");

        assertEquals(
                LocalDateTime.of(2026, 5, 31, 0, 0),
                routerAgent.route(request).getQueryEventRequest().getStartTime()
        );
        assertEquals(
                LocalDateTime.of(2026, 6, 1, 0, 0),
                routerAgent.route(request).getQueryEventRequest().getEndTime()
        );
    }

    @Test
    void shouldAskForWeatherLocationInsteadOfUsingInventedLocation() {
        SkillDefinition weather = new SkillDefinition();
        weather.setSkillId("weather.query");
        when(skillRegistry.get("weather.query")).thenReturn(weather);
        when(jsonExtractor.extractObject("raw")).thenReturn(
                "{\"intent\":\"RUN_SKILLS\",\"skillCalls\":[{"
                        + "\"stepOrder\":10,\"skillId\":\"weather.query\","
                        + "\"outputAlias\":\"weather\",\"arguments\":{\"location\":\"上海\"}}]}"
        );
        AgentExecuteRequest request = new AgentExecuteRequest();
        request.setUserId(1L);
        request.setText("天气");
        request.setCurrentTime("2026-05-31 12:00:00");

        assertEquals(Collections.singletonList("location"), routerAgent.route(request).getMissingFields());
        assertFalse(routerAgent.route(request).getToolSteps().get(0).getArguments().containsKey("location"));
    }

    @Test
    void shouldUseExplicitChineseWeatherLocationInsteadOfNormalizedModelValue() {
        SkillDefinition weather = new SkillDefinition();
        weather.setSkillId("weather.query");
        when(skillRegistry.get("weather.query")).thenReturn(weather);
        when(jsonExtractor.extractObject("raw")).thenReturn(
                "{\"intent\":\"RUN_SKILLS\",\"skillCalls\":[{"
                        + "\"stepOrder\":10,\"skillId\":\"weather.query\","
                        + "\"outputAlias\":\"weather\",\"arguments\":{\"location\":\"Shanghai\"}}]}"
        );
        AgentExecuteRequest request = new AgentExecuteRequest();
        request.setUserId(1L);
        request.setText("上海天气怎么样");
        request.setCurrentTime("2026-05-31 12:00:00");

        assertEquals(
                "上海",
                routerAgent.route(request).getToolSteps().get(0).getArguments().get("location")
        );
    }

    @Test
    void shouldNotAskForTimeWhenRecentEventIsUpgradedToTencentMeeting() {
        when(jsonExtractor.extractObject("raw")).thenReturn(
                "{\"intent\":\"UPDATE_EVENT\",\"slots\":{"
                        + "\"targetReference\":\"LAST_MENTIONED_EVENT\",\"onlineMeeting\":true},"
                        + "\"missingFields\":[\"title\",\"start_time\",\"end_time\",\"update_fields\"]}"
        );
        AgentExecuteRequest request = new AgentExecuteRequest();
        request.setUserId(1L);
        request.setText("帮我将刚才的会议修改为腾讯会议，然后把会议链接发我");
        request.setCurrentTime("2026-05-31 12:00:00");

        assertEquals(Collections.emptyList(), routerAgent.route(request).getMissingFields());
        assertEquals(
                "LAST_MENTIONED_EVENT",
                routerAgent.route(request).getEventResolveRequest().getReference()
        );
    }
}
