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
import com.voice.agent.model.dto.RouterSkillCallDTO;
import com.voice.agent.model.dto.RouterSlots;
import com.voice.agent.model.dto.UpdateEventRequest;
import com.voice.agent.skill.SkillDefinition;
import com.voice.agent.skill.SkillRegistry;
import com.voice.agent.tool.ToolActionStep;
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
    private static final Pattern WEATHER_FILLER_PATTERN = Pattern.compile(
            "(请问|请|帮我|告诉我|查一下|查询|查查|查|看一下|看看|当前|现在|今天|今日|明天|后天|未来|"
                    + "天气预报|天气|气温|温度|情况|怎么样|如何|是什么|一下|的)"
    );
    private static final Pattern NAVIGATION_PATTERN = Pattern.compile("(?:导航到|带我去|前往|去)(.+)");
    private static final Pattern QUERY_TIME_HINT_PATTERN = Pattern.compile(
            "(今天|明天|后天|凌晨|早上|上午|中午|下午|晚上|未来|本周|这周|下周|本月|这个月|下个月|今年|"
                    + "[0-9]{1,4}[年/-]|[0-9]{1,2}(?:月|号|日|点))"
    );
    private static final Pattern RELATIVE_MINUTES_PATTERN = Pattern.compile("([0-9]+)\\s*分钟(?:之后|以后|后)");
    private static final Pattern DURATION_PATTERN = Pattern.compile("(?:时长|持续)(?:为|是)?\\s*([0-9]+)\\s*(小时|分钟)");

    private final LlmClient llmClient;
    private final LlmJsonExtractor jsonExtractor;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;
    private final DefaultValueResolver defaultValueResolver;
    private final SkillRegistry skillRegistry;

    @Value("${llm.router.enabled:true}")
    private Boolean enabled;

    public LlmRouterAgent(
            LlmClient llmClient,
            LlmJsonExtractor jsonExtractor,
            PromptTemplateService promptTemplateService,
            ObjectMapper objectMapper,
            DefaultValueResolver defaultValueResolver,
            SkillRegistry skillRegistry
    ) {
        this.llmClient = llmClient;
        this.jsonExtractor = jsonExtractor;
        this.promptTemplateService = promptTemplateService;
        this.objectMapper = objectMapper;
        this.defaultValueResolver = defaultValueResolver;
        this.skillRegistry = skillRegistry;
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
        variables.put("skills", buildSkillCatalog());
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
        plan.setToolSteps(convertSkillCalls(response.getSkillCalls()));

        RouterSlots slots = response.getSlots() == null ? new RouterSlots() : response.getSlots();
        if (AgentConstants.INTENT_CREATE_EVENT.equals(response.getIntent())) {
            fillCreatePlan(plan, slots, request);
        } else if (AgentConstants.INTENT_QUERY_EVENT.equals(response.getIntent())) {
            fillQueryPlan(plan, slots, request);
        } else if (AgentConstants.INTENT_UPDATE_EVENT.equals(response.getIntent())) {
            fillUpdatePlan(plan, slots, request);
        } else if (AgentConstants.INTENT_DELETE_EVENT.equals(response.getIntent())) {
            fillDeletePlan(plan, slots, request);
        } else if (AgentConstants.INTENT_RUN_SKILLS.equals(response.getIntent())) {
            plan.setActionType(AgentConstants.ACTION_RUN_SKILLS);
            plan.setNeedConfirm(false);
            if (plan.getToolSteps().isEmpty()) {
                plan.getMissingFields().add("skill_calls");
            }
            validateExplicitToolArguments(plan, request);
        } else {
            plan.setIntent(AgentConstants.INTENT_UNKNOWN);
            if (plan.getMissingFields().isEmpty()) {
                plan.getMissingFields().add("intent");
            }
        }
        return plan;
    }

    /**
     * 对直接执行的工具参数做最小可信校验，避免模型自行补出用户没有提供的地点。
     */
    private void validateExplicitToolArguments(AgentPlan plan, AgentExecuteRequest request) {
        for (ToolActionStep step : plan.getToolSteps()) {
            if ("weather.query".equals(step.getSkillId())) {
                requireExplicitArgument(plan, step, "location", extractWeatherLocation(request));
            } else if ("navigation.route".equals(step.getSkillId())) {
                requireExplicitArgument(plan, step, "destination", extractNavigationDestination(request));
            }
        }
    }

    private void requireExplicitArgument(
            AgentPlan plan,
            ToolActionStep step,
            String argumentName,
            String explicitValue
    ) {
        Map<String, Object> arguments = step.getArguments();
        if (arguments == null) {
            arguments = new HashMap<>();
            step.setArguments(arguments);
        }
        if (StringUtils.hasText(explicitValue)) {
            arguments.put(argumentName, explicitValue);
            return;
        }
        arguments.remove(argumentName);
        if (!plan.getMissingFields().contains(argumentName)) {
            plan.getMissingFields().add(argumentName);
        }
    }

    private String extractWeatherLocation(AgentExecuteRequest request) {
        if (request == null || !StringUtils.hasText(request.getText())) {
            return null;
        }
        String location = WEATHER_FILLER_PATTERN.matcher(request.getText()).replaceAll("");
        location = location.replaceAll("[\\s,，。！？?；;：:]+", "").trim();
        return StringUtils.hasText(location) ? location : null;
    }

    private String extractNavigationDestination(AgentExecuteRequest request) {
        if (request == null || !StringUtils.hasText(request.getText())) {
            return null;
        }
        Matcher matcher = NAVIGATION_PATTERN.matcher(request.getText());
        if (!matcher.find()) {
            return null;
        }
        String destination = matcher.group(1).replaceAll("[\\s,，。！？?；;：:]+$", "").trim();
        return StringUtils.hasText(destination) ? destination : null;
    }

    private void fillCreatePlan(AgentPlan plan, RouterSlots slots, AgentExecuteRequest request) {
        plan.setActionType(AgentConstants.ACTION_CREATE_EVENT);
        plan.setNeedConfirm(true);

        LocalDateTime rawStartTime = parseDateTime(slots.getStartTime());
        LocalDateTime rawEndTime = parseDateTime(slots.getEndTime());
        LocalDateTime relativeStart = parseRelativeStart(request);
        LocalDateTime startTime = relativeStart == null ? normalizeRelativeDate(rawStartTime, request) : relativeStart;
        Duration explicitDuration = parseExplicitDuration(request);
        LocalDateTime endTime = explicitDuration != null && startTime != null
                ? startTime.plus(explicitDuration)
                : relativeStart == null ? normalizeRelativeEnd(rawStartTime, rawEndTime, startTime, request) : null;

        CreateEventRequest createRequest = new CreateEventRequest();
        createRequest.setUserId(defaultValueResolver.resolveUserId(request.getUserId()));
        createRequest.setTitle(resolveCreateTitle(slots.getTitle(), request.getText()));
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
        createRequest.setPlannedToolSteps(plan.getToolSteps());
        plan.setCreateEventRequest(createRequest);

        if (StringUtils.hasText(createRequest.getTitle())) {
            plan.getMissingFields().removeIf("title"::equals);
        } else if (!plan.getMissingFields().contains("title")) {
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
        boolean allEvents = isAllEventQuery(request.getText());
        if (!allEvents) {
            LocalDateTime rawStartTime = parseDateTime(slots.getQueryStartTime());
            LocalDateTime rawEndTime = parseDateTime(slots.getQueryEndTime());
            LocalDateTime startTime = normalizeRelativeDate(rawStartTime, request);
            queryRequest.setStartTime(startTime);
            queryRequest.setEndTime(normalizeRelativeEnd(rawStartTime, rawEndTime, startTime, request));
            queryRequest.setKeyword(trimToNull(slots.getKeyword()));
        }
        plan.setQueryEventRequest(queryRequest);

        if (allEvents) {
            plan.getMissingFields().removeIf(field -> "start_time".equals(field) || "end_time".equals(field));
        } else {
            if (queryRequest.getStartTime() == null && !plan.getMissingFields().contains("start_time")) {
                plan.getMissingFields().add("start_time");
            }
            if (queryRequest.getEndTime() == null && !plan.getMissingFields().contains("end_time")) {
                plan.getMissingFields().add("end_time");
            }
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
        updateRequest.setTitle(trimToNull(slots.getTitle()));
        updateRequest.setLocation(trimToNull(slots.getLocation()));
        updateRequest.setDescription(trimToNull(slots.getDescription()));
        updateRequest.setMeetingUrl(trimToNull(slots.getMeetingUrl()));
        updateRequest.setOnlineMeeting(Boolean.TRUE.equals(slots.getOnlineMeeting()) || containsOnlineMeetingIntent(request.getText()));
        updateRequest.setPlannedToolSteps(plan.getToolSteps());
        plan.setUpdateEventRequest(updateRequest);

        ensureResolveCriteria(plan);
        boolean hasMutation = hasUpdateMutation(updateRequest) || !plan.getToolSteps().isEmpty();
        if (hasMutation) {
            plan.getMissingFields().removeIf(field ->
                    "start_time".equals(field)
                            || "end_time".equals(field)
                            || "update_fields".equals(field)
            );
        } else {
            plan.getMissingFields().add("update_fields");
        }
        ensureMutationSteps(plan, "UPDATE_EVENT", "calendar.update");
    }

    private void fillDeletePlan(AgentPlan plan, RouterSlots slots, AgentExecuteRequest request) {
        plan.setActionType(AgentConstants.ACTION_DELETE_EVENT);
        plan.setNeedConfirm(true);
        EventResolveRequest resolveRequest = buildResolveRequest(slots, request);
        markDeleteAllMatches(resolveRequest, request.getText());
        plan.setEventResolveRequest(resolveRequest);
        ensureResolveCriteria(plan);
        ensureMutationSteps(plan, "DELETE_EVENT", "calendar.delete");
    }

    private EventResolveRequest buildResolveRequest(RouterSlots slots, AgentExecuteRequest request) {
        EventResolveRequest resolveRequest = new EventResolveRequest();
        resolveRequest.setUserId(defaultValueResolver.resolveUserId(request.getUserId()));
        resolveRequest.setTitleKeyword(trimToNull(slots.getTargetTitle()));
        resolveRequest.setReference(trimToNull(slots.getTargetReference()));
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
        boolean hasReference = StringUtils.hasText(request.getReference()) || request.getEventId() != null;
        if (hasTitle || hasTimeRange || hasReference) {
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

    private List<ToolActionStep> convertSkillCalls(List<RouterSkillCallDTO> input) {
        List<ToolActionStep> steps = new ArrayList<>();
        if (input == null) {
            return steps;
        }
        for (RouterSkillCallDTO call : input) {
            if (call == null || !StringUtils.hasText(call.getSkillId())) {
                continue;
            }
            skillRegistry.get(call.getSkillId());
            ToolActionStep step = ToolActionStep.of(
                    call.getStepOrder() == null ? 10 + steps.size() * 10 : call.getStepOrder(),
                    call.getSkillId().trim(),
                    trimToNull(call.getOutputAlias()),
                    call.getArguments()
            );
            step.setOnFailure(StringUtils.hasText(call.getOnFailure()) ? call.getOnFailure() : "STOP");
            steps.add(step);
        }
        return steps;
    }

    private String buildSkillCatalog() {
        try {
            List<SkillDefinition> enabled = new ArrayList<>();
            for (SkillDefinition skill : skillRegistry.list()) {
                if (Boolean.TRUE.equals(skill.getEnabled())) {
                    enabled.add(skill);
                }
            }
            return objectMapper.writeValueAsString(enabled);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Skill Registry 序列化失败", e);
        }
    }

    private boolean hasUpdateMutation(UpdateEventRequest request) {
        return request.getStartTime() != null
                || request.getEndTime() != null
                || StringUtils.hasText(request.getTitle())
                || StringUtils.hasText(request.getLocation())
                || StringUtils.hasText(request.getDescription())
                || StringUtils.hasText(request.getMeetingUrl())
                || Boolean.TRUE.equals(request.getOnlineMeeting());
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

    private LocalDateTime parseRelativeStart(AgentExecuteRequest request) {
        if (request == null || !StringUtils.hasText(request.getText())) {
            return null;
        }
        Matcher matcher = RELATIVE_MINUTES_PATTERN.matcher(request.getText());
        return matcher.find() ? parseCurrentTime(request.getCurrentTime()).plusMinutes(Long.parseLong(matcher.group(1))) : null;
    }

    private Duration parseExplicitDuration(AgentExecuteRequest request) {
        if (request == null || !StringUtils.hasText(request.getText())) {
            return null;
        }
        Matcher matcher = DURATION_PATTERN.matcher(request.getText());
        if (!matcher.find()) {
            return null;
        }
        long amount = Long.parseLong(matcher.group(1));
        return "小时".equals(matcher.group(2)) ? Duration.ofHours(amount) : Duration.ofMinutes(amount);
    }

    private String resolveCreateTitle(String modelTitle, String text) {
        if (StringUtils.hasText(text) && text.contains("腾讯会议")) {
            return "腾讯会议";
        }
        return trimToNull(modelTitle);
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

    private boolean isAllEventQuery(String text) {
        return StringUtils.hasText(text)
                && (text.contains("\u6240\u6709\u65e5\u7a0b")
                || text.contains("\u5168\u90e8\u65e5\u7a0b")
                || text.contains("\u6240\u6709\u7684\u65e5\u7a0b")
                || text.contains("\u5168\u90e8\u7684\u65e5\u7a0b")
                || text.contains("\u6240\u6709\u5b89\u6392")
                || text.contains("\u5168\u90e8\u5b89\u6392")
                || text.contains("\u6240\u6709\u7684\u5b89\u6392")
                || text.contains("\u5168\u90e8\u7684\u5b89\u6392")
                || text.contains("\u65e5\u7a0b\u5217\u8868"))
                && !QUERY_TIME_HINT_PATTERN.matcher(text).find();
    }

    private boolean containsOnlineMeetingIntent(String text) {
        return StringUtils.hasText(text)
                && ((text.contains("线上") && text.contains("会"))
                || text.contains("会议链接")
                || text.contains("腾讯会议"));
    }

    private void markDeleteAllMatches(EventResolveRequest request, String text) {
        if (request == null) {
            return;
        }
        if (isGenericDeleteTitle(request.getTitleKeyword())) {
            request.setTitleKeyword(null);
        }
        boolean hasRange = request.getRangeStart() != null && request.getRangeEnd() != null;
        boolean hasSingleTarget = StringUtils.hasText(request.getTitleKeyword())
                || StringUtils.hasText(request.getReference())
                || request.getEventId() != null;
        boolean explicitlyAll = StringUtils.hasText(text) && (text.contains("所有") || text.contains("全部"));
        request.setDeleteAllMatches(hasRange && (!hasSingleTarget || explicitlyAll));
    }

    private boolean isGenericDeleteTitle(String title) {
        return "日程".equals(title) || "安排".equals(title) || "所有日程".equals(title) || "全部日程".equals(title);
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
