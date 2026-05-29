package com.voice.agent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voice.agent.llm.LlmClient;
import com.voice.agent.llm.LlmJsonExtractor;
import com.voice.agent.llm.PromptTemplateService;
import com.voice.agent.model.dto.AgentExecuteRequest;
import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.dto.QueryEventRequest;
import com.voice.agent.model.dto.RouterPlanResponse;
import com.voice.agent.model.dto.RouterPlanStepDTO;
import com.voice.agent.model.dto.RouterSlots;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 版路由智能体。
 *
 * <p>它只负责把自然语言转成结构化 AgentPlan，不执行任何业务动作。</p>
 */
@Component
public class LlmRouterAgent {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ROUTER_PROMPT = "prompts/router-agent-prompt.md";

    private final LlmClient llmClient;
    private final LlmJsonExtractor jsonExtractor;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;
    private final DefaultValueResolver defaultValueResolver;

    @Value("${llm.router.enabled:true}")
    private Boolean enabled;

    public LlmRouterAgent(
            LlmClient llmClient,
            LlmJsonExtractor jsonExtractor,
            PromptTemplateService promptTemplateService,
            ObjectMapper objectMapper,
            DefaultValueResolver defaultValueResolver
    ) {
        this.llmClient = llmClient;
        this.jsonExtractor = jsonExtractor;
        this.promptTemplateService = promptTemplateService;
        this.objectMapper = objectMapper;
        this.defaultValueResolver = defaultValueResolver;
    }

    public AgentPlan route(AgentExecuteRequest request) {
        if (!Boolean.TRUE.equals(enabled)) {
            throw new IllegalStateException("LLM Router 未启用");
        }

        String prompt = promptTemplateService.render(ROUTER_PROMPT, buildPromptVariables(request));
        String raw = llmClient.chat(prompt, "请根据上面的规则解析用户输入。");
        String json = jsonExtractor.extractObject(raw);
        RouterPlanResponse response = readResponse(json);
        return toAgentPlan(response, request);
    }

    private Map<String, String> buildPromptVariables(AgentExecuteRequest request) {
        Map<String, String> variables = new HashMap<>();
        variables.put("current_time", request.getCurrentTime());
        variables.put("timezone", StringUtils.hasText(request.getTimezone()) ? request.getTimezone() : "Asia/Shanghai");
        variables.put("text", request.getText());
        return variables;
    }

    private RouterPlanResponse readResponse(String json) {
        try {
            return objectMapper.readValue(json, RouterPlanResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("LLM Router JSON 解析失败", e);
        }
    }

    private AgentPlan toAgentPlan(RouterPlanResponse response, AgentExecuteRequest request) {
        if (response == null || !StringUtils.hasText(response.getIntent())) {
            throw new IllegalArgumentException("LLM Router 缺少 intent");
        }

        AgentPlan plan = new AgentPlan();
        plan.setIntent(response.getIntent());
        plan.setTargetAgent(StringUtils.hasText(response.getTargetAgent())
                ? response.getTargetAgent()
                : AgentConstants.TARGET_CALENDAR_AGENT);
        plan.setActionType(response.getActionType());
        plan.setNeedConfirm(Boolean.TRUE.equals(response.getNeedConfirm()));
        plan.setMissingFields(copyList(response.getMissingFields()));
        plan.setSteps(convertSteps(response.getSteps(), response.getIntent()));

        RouterSlots slots = response.getSlots() == null ? new RouterSlots() : response.getSlots();
        if (AgentConstants.INTENT_CREATE_EVENT.equals(response.getIntent())) {
            fillCreatePlan(plan, slots, request);
        } else if (AgentConstants.INTENT_QUERY_EVENT.equals(response.getIntent())) {
            fillQueryPlan(plan, slots, request);
        } else {
            plan.setIntent(AgentConstants.INTENT_UNKNOWN);
            if (plan.getMissingFields().isEmpty()) {
                plan.getMissingFields().add("intent");
            }
        }
        return plan;
    }

    private void fillCreatePlan(AgentPlan plan, RouterSlots slots, AgentExecuteRequest request) {
        plan.setActionType(AgentConstants.ACTION_CREATE_EVENT);
        plan.setNeedConfirm(true);

        LocalDateTime startTime = parseDateTime(slots.getStartTime());
        LocalDateTime endTime = defaultValueResolver.resolveEndTime(startTime, parseDateTime(slots.getEndTime()));

        CreateEventRequest createRequest = new CreateEventRequest();
        createRequest.setUserId(defaultValueResolver.resolveUserId(request.getUserId()));
        createRequest.setTitle(trimToNull(slots.getTitle()));
        createRequest.setStartTime(startTime);
        createRequest.setEndTime(endTime);
        createRequest.setLocation(trimToNull(slots.getLocation()));
        createRequest.setDescription(trimToNull(slots.getDescription()));
        createRequest.setMeetingUrl(trimToNull(slots.getMeetingUrl()));
        createRequest.setReminderMinutes(defaultValueResolver.resolveReminderMinutes(slots.getReminderMinutes()));
        createRequest.setSource("AGENT");
        plan.setCreateEventRequest(createRequest);

        if (!StringUtils.hasText(createRequest.getTitle()) && !plan.getMissingFields().contains("title")) {
            plan.getMissingFields().add("title");
        }
        if (startTime == null && !plan.getMissingFields().contains("start_time")) {
            plan.getMissingFields().add("start_time");
        }
        ensureCreateSteps(plan);
    }

    private void fillQueryPlan(AgentPlan plan, RouterSlots slots, AgentExecuteRequest request) {
        plan.setActionType(AgentConstants.ACTION_QUERY_EVENT);
        plan.setNeedConfirm(false);

        QueryEventRequest queryRequest = new QueryEventRequest();
        queryRequest.setUserId(defaultValueResolver.resolveUserId(request.getUserId()));
        queryRequest.setStartTime(parseDateTime(slots.getQueryStartTime()));
        queryRequest.setEndTime(parseDateTime(slots.getQueryEndTime()));
        queryRequest.setKeyword(trimToNull(slots.getKeyword()));
        plan.setQueryEventRequest(queryRequest);

        if (queryRequest.getStartTime() == null && !plan.getMissingFields().contains("start_time")) {
            plan.getMissingFields().add("start_time");
        }
        if (queryRequest.getEndTime() == null && !plan.getMissingFields().contains("end_time")) {
            plan.getMissingFields().add("end_time");
        }
        ensureQuerySteps(plan);
    }

    private List<AgentPlanStep> convertSteps(List<RouterPlanStepDTO> input, String intent) {
        List<AgentPlanStep> steps = new ArrayList<>();
        if (input != null) {
            for (RouterPlanStepDTO item : input) {
                if (item == null) {
                    continue;
                }
                steps.add(AgentPlanStep.of(
                        item.getStepOrder(),
                        item.getAgentName(),
                        item.getAction(),
                        item.getSkillId()
                ));
            }
        }
        if (steps.isEmpty()) {
            steps.add(AgentPlanStep.of(1, "RouterAgent", "ROUTE", "router.route"));
            if (AgentConstants.INTENT_CREATE_EVENT.equals(intent)) {
                steps.add(AgentPlanStep.of(2, "CalendarAgent", "CHECK_CONFLICT", "calendar.conflict_check"));
                steps.add(AgentPlanStep.of(3, "CalendarAgent", "CREATE_EVENT", "calendar.create"));
            } else if (AgentConstants.INTENT_QUERY_EVENT.equals(intent)) {
                steps.add(AgentPlanStep.of(2, "CalendarAgent", "QUERY_EVENT", "calendar.query"));
            }
        }
        return steps;
    }

    private void ensureCreateSteps(AgentPlan plan) {
        if (plan.getSteps().stream().noneMatch(step -> "CREATE_EVENT".equals(step.getAction()))) {
            plan.getSteps().add(AgentPlanStep.of(2, "CalendarAgent", "CHECK_CONFLICT", "calendar.conflict_check"));
            plan.getSteps().add(AgentPlanStep.of(3, "CalendarAgent", "CREATE_EVENT", "calendar.create"));
        }
    }

    private void ensureQuerySteps(AgentPlan plan) {
        if (plan.getSteps().stream().noneMatch(step -> "QUERY_EVENT".equals(step.getAction()))) {
            plan.getSteps().add(AgentPlanStep.of(2, "CalendarAgent", "QUERY_EVENT", "calendar.query"));
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> copyList(List<String> input) {
        return input == null ? new ArrayList<>() : new ArrayList<>(input);
    }
}
