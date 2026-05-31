package com.voice.agent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voice.agent.llm.LlmClient;
import com.voice.agent.llm.LlmJsonExtractor;
import com.voice.agent.llm.PromptTemplateService;
import com.voice.agent.model.dto.AgentExecuteRequest;
import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.dto.EventResolveRequest;
import com.voice.agent.model.dto.QueryEventRequest;
import com.voice.agent.model.dto.RouterPlanResponse;
import com.voice.agent.model.dto.RouterPlanStepDTO;
import com.voice.agent.model.dto.RouterSlots;
import com.voice.agent.model.dto.UpdateEventRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 版路由智能体。
 *
 * <p>它只负责把自然语言转成结构化 AgentPlan，不执行任何业务动作。</p>
 */
@Component
public class LlmRouterAgent {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String ROUTER_PROMPT = "prompts/router-agent-prompt.md";
    private static final Pattern SMS_RECEIVER_PATTERN = Pattern.compile(
            "(?:短信提醒|发短信给)([\\u4e00-\\u9fa5A-Za-z0-9_]{1,20})"
    );

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
        variables.put("history", StringUtils.hasText(request.getHistory()) ? request.getHistory() : "无");
        variables.put("conversation_state", StringUtils.hasText(request.getConversationState()) ? request.getConversationState() : "无");
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
        plan.setMissingFields(normalizeMissingFields(response.getMissingFields()));
        plan.setSteps(convertSteps(response.getSteps(), response.getIntent()));

        RouterSlots slots = response.getSlots() == null ? new RouterSlots() : response.getSlots();
        if (AgentConstants.INTENT_CREATE_EVENT.equals(response.getIntent())) {
            fillCreatePlan(plan, slots, request);
        } else if (AgentConstants.INTENT_QUERY_EVENT.equals(response.getIntent())) {
            fillQueryPlan(plan, slots, request);
        } else if (AgentConstants.INTENT_UPDATE_EVENT.equals(response.getIntent())) {
            fillUpdatePlan(plan, slots, request);
        } else if (AgentConstants.INTENT_DELETE_EVENT.equals(response.getIntent())) {
            fillDeletePlan(plan, slots, request);
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

        LocalDateTime rawStartTime = parseDateTime(slots.getStartTime());
        LocalDateTime rawEndTime = parseDateTime(slots.getEndTime());
        LocalDateTime startTime = normalizeRelativeDate(rawStartTime, request);
        LocalDateTime endTime = normalizeRelativeEnd(rawStartTime, rawEndTime, startTime, request);

        CreateEventRequest createRequest = new CreateEventRequest();
        createRequest.setUserId(defaultValueResolver.resolveUserId(request.getUserId()));
        createRequest.setTitle(trimToNull(slots.getTitle()));
        createRequest.setStartTime(startTime);
        createRequest.setEndTime(endTime);
        createRequest.setLocation(trimToNull(slots.getLocation()));
        createRequest.setDescription(trimToNull(slots.getDescription()));
        createRequest.setMeetingUrl(trimToNull(slots.getMeetingUrl()));
        createRequest.setReminderMinutes(slots.getReminderMinutes());
        createRequest.setSource("AGENT");
        createRequest.setRecurrenceType(trimToNull(slots.getRecurrenceType()));
        createRequest.setRecurrenceInterval(slots.getRecurrenceInterval());
        createRequest.setRecurrenceCount(slots.getRecurrenceCount());
        createRequest.setRecurrenceUntil(parseDate(slots.getRecurrenceUntil()));
        createRequest.setOnlineMeeting(Boolean.TRUE.equals(slots.getOnlineMeeting()) || containsOnlineMeetingIntent(request.getText()));
        createRequest.setSmsReceiver(resolveSmsReceiver(slots.getSmsReceiver(), request.getText()));
        createRequest.setSmsContent(trimToNull(slots.getSmsContent()));
        createRequest.setEmailReceiver(trimToNull(slots.getEmailReceiver()));
        createRequest.setEmailContent(trimToNull(slots.getEmailContent()));
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
        LocalDateTime rawStartTime = parseDateTime(slots.getQueryStartTime());
        LocalDateTime rawEndTime = parseDateTime(slots.getQueryEndTime());
        LocalDateTime startTime = normalizeRelativeDate(rawStartTime, request);
        queryRequest.setStartTime(startTime);
        queryRequest.setEndTime(normalizeRelativeEnd(rawStartTime, rawEndTime, startTime, request));
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

    private void fillUpdatePlan(AgentPlan plan, RouterSlots slots, AgentExecuteRequest request) {
        plan.setActionType(AgentConstants.ACTION_UPDATE_EVENT);
        plan.setNeedConfirm(true);
        plan.setEventResolveRequest(buildResolveRequest(slots, request));

        LocalDateTime rawStartTime = parseDateTime(slots.getNewStartTime());
        LocalDateTime rawEndTime = parseDateTime(slots.getNewEndTime());
        LocalDateTime newStart = normalizeRelativeDate(rawStartTime, request);
        UpdateEventRequest updateRequest = new UpdateEventRequest();
        updateRequest.setUserId(defaultValueResolver.resolveUserId(request.getUserId()));
        updateRequest.setStartTime(newStart);
        updateRequest.setEndTime(defaultValueResolver.resolveEndTime(
                newStart,
                normalizeRelativeEnd(rawStartTime, rawEndTime, newStart, request)
        ));
        plan.setUpdateEventRequest(updateRequest);

        ensureResolveCriteria(plan);
        if (newStart == null && !plan.getMissingFields().contains("start_time")) {
            plan.getMissingFields().add("start_time");
        }
        ensureMutationSteps(plan, "UPDATE_EVENT", "calendar.update");
    }

    private void fillDeletePlan(AgentPlan plan, RouterSlots slots, AgentExecuteRequest request) {
        plan.setActionType(AgentConstants.ACTION_DELETE_EVENT);
        plan.setNeedConfirm(true);
        plan.setEventResolveRequest(buildResolveRequest(slots, request));
        ensureResolveCriteria(plan);
        ensureMutationSteps(plan, "DELETE_EVENT", "calendar.delete");
    }

    private EventResolveRequest buildResolveRequest(RouterSlots slots, AgentExecuteRequest request) {
        EventResolveRequest resolveRequest = new EventResolveRequest();
        resolveRequest.setUserId(defaultValueResolver.resolveUserId(request.getUserId()));
        resolveRequest.setTitleKeyword(trimToNull(slots.getTargetTitle()));
        LocalDateTime rawStartTime = parseDateTime(slots.getTargetStartTime());
        LocalDateTime rawEndTime = parseDateTime(slots.getTargetEndTime());
        LocalDateTime startTime = normalizeRelativeDate(rawStartTime, request);
        resolveRequest.setRangeStart(startTime);
        resolveRequest.setRangeEnd(normalizeRelativeEnd(rawStartTime, rawEndTime, startTime, request));
        return resolveRequest;
    }

    private void ensureResolveCriteria(AgentPlan plan) {
        EventResolveRequest request = plan.getEventResolveRequest();
        boolean hasTitle = StringUtils.hasText(request.getTitleKeyword());
        boolean hasTimeRange = request.getRangeStart() != null && request.getRangeEnd() != null;
        if (hasTitle || hasTimeRange) {
            plan.getMissingFields().removeIf("title"::equals);
        } else if (!plan.getMissingFields().contains("title")) {
            plan.getMissingFields().add("title");
        }
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
            } else if (AgentConstants.INTENT_UPDATE_EVENT.equals(intent)) {
                steps.add(AgentPlanStep.of(2, "CalendarAgent", "RESOLVE_EVENT", "calendar.event.resolve"));
                steps.add(AgentPlanStep.of(3, "CalendarAgent", "UPDATE_EVENT", "calendar.update"));
            } else if (AgentConstants.INTENT_DELETE_EVENT.equals(intent)) {
                steps.add(AgentPlanStep.of(2, "CalendarAgent", "RESOLVE_EVENT", "calendar.event.resolve"));
                steps.add(AgentPlanStep.of(3, "CalendarAgent", "DELETE_EVENT", "calendar.delete"));
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

    private void ensureMutationSteps(AgentPlan plan, String action, String skillId) {
        if (plan.getSteps().stream().noneMatch(step -> action.equals(step.getAction()))) {
            plan.getSteps().add(AgentPlanStep.of(2, "CalendarAgent", "RESOLVE_EVENT", "calendar.event.resolve"));
            plan.getSteps().add(AgentPlanStep.of(3, "CalendarAgent", action, skillId));
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

    private LocalDateTime normalizeRelativeDate(LocalDateTime value, AgentExecuteRequest request) {
        if (value == null || request == null || !StringUtils.hasText(request.getText())) {
            return value;
        }
        LocalDate expectedDate = null;
        LocalDate currentDate = parseCurrentTime(request.getCurrentTime()).toLocalDate();
        if (request.getText().contains("后天")) {
            expectedDate = currentDate.plusDays(2);
        } else if (request.getText().contains("明天")) {
            expectedDate = currentDate.plusDays(1);
        } else if (request.getText().contains("今天")) {
            expectedDate = currentDate;
        }
        return expectedDate == null ? value : LocalDateTime.of(expectedDate, value.toLocalTime());
    }

    private LocalDateTime normalizeRelativeEnd(
            LocalDateTime rawStart,
            LocalDateTime rawEnd,
            LocalDateTime normalizedStart,
            AgentExecuteRequest request
    ) {
        if (rawStart != null && rawEnd != null && rawEnd.isAfter(rawStart) && normalizedStart != null) {
            return normalizedStart.plus(Duration.between(rawStart, rawEnd));
        }
        return normalizeRelativeDate(rawEnd, request);
    }

    private LocalDateTime parseCurrentTime(String value) {
        return StringUtils.hasText(value)
                ? LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER)
                : LocalDateTime.now();
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return LocalDate.parse(value.trim(), DATE_FORMATTER);
    }

    private List<String> normalizeMissingFields(List<String> input) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String field : copyList(input)) {
            if (!StringUtils.hasText(field)) {
                continue;
            }
            String trimmed = field.trim();
            if ("startTime".equals(trimmed)
                    || "newStartTime".equals(trimmed)
                    || "queryStartTime".equals(trimmed)
                    || "targetStartTime".equals(trimmed)) {
                normalized.add("start_time");
            } else if ("endTime".equals(trimmed)
                    || "newEndTime".equals(trimmed)
                    || "queryEndTime".equals(trimmed)
                    || "targetEndTime".equals(trimmed)) {
                normalized.add("end_time");
            } else if ("targetTitle".equals(trimmed)) {
                normalized.add("title");
            } else {
                normalized.add(trimmed);
            }
        }
        return new ArrayList<>(normalized);
    }

    private boolean containsOnlineMeetingIntent(String text) {
        return StringUtils.hasText(text)
                && ((text.contains("线上") && text.contains("会")) || text.contains("会议链接"));
    }

    private String resolveSmsReceiver(String slotValue, String text) {
        if (StringUtils.hasText(slotValue)) {
            return slotValue.trim();
        }
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = SMS_RECEIVER_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
