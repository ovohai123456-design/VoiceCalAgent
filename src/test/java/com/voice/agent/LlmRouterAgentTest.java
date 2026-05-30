package com.voice.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voice.agent.agent.DefaultValueResolver;
import com.voice.agent.agent.LlmRouterAgent;
import com.voice.agent.llm.LlmClient;
import com.voice.agent.llm.LlmJsonExtractor;
import com.voice.agent.llm.PromptTemplateService;
import com.voice.agent.model.dto.AgentExecuteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    private LlmRouterAgent routerAgent;

    @BeforeEach
    void setUp() {
        routerAgent = new LlmRouterAgent(
                llmClient,
                jsonExtractor,
                promptTemplateService,
                new ObjectMapper(),
                defaultValueResolver
        );
        ReflectionTestUtils.setField(routerAgent, "enabled", true);
        when(promptTemplateService.render(anyString(), any())).thenReturn("prompt");
        when(llmClient.chat(anyString(), anyString())).thenReturn("raw");
        when(jsonExtractor.extractObject("raw")).thenReturn(
                "{\"intent\":\"CREATE_EVENT\",\"slots\":{\"title\":\"项目会\"},"
                        + "\"missingFields\":[\"startTime\",\"start_time\",\"\",\"startTime\"]}"
        );
        when(defaultValueResolver.resolveUserId(1L)).thenReturn(1L);
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
}
