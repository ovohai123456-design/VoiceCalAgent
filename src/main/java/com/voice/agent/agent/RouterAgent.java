package com.voice.agent.agent;

import com.voice.agent.model.dto.AgentExecuteRequest;
import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.dto.EventResolveRequest;
import com.voice.agent.model.dto.QueryEventRequest;
import com.voice.agent.model.dto.UpdateEventRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 路由智能体。
 *
 * <p>优先使用 LLM 解析自然语言为 AgentPlan；LLM 不可用或 JSON 异常时，自动回退到规则解析。</p>
 */
@Component
public class RouterAgent {
    private static final Logger log = LoggerFactory.getLogger(RouterAgent.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern HOUR_PATTERN = Pattern.compile("(凌晨|早上|上午|中午|下午|晚上)?([0-9]{1,2}|[一二两三四五六七八九十]{1,3})点");
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})(?:号|日)?");
    private static final Pattern DAY_OF_MONTH_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,2})(?:号|日)");
    private static final String DATE_EXPRESSION_REGEX = "(今天|明天|后天|(?:[0-9]{4}年)?[0-9]{1,2}月[0-9]{1,2}[号日]?|[0-9]{1,2}[号日])";
    private static final Pattern SMS_RECEIVER_PATTERN = Pattern.compile(
            "(?:短信提醒|发短信给)([\\u4e00-\\u9fa5A-Za-z0-9_]{1,20})"
    );

    private final DefaultValueResolver defaultValueResolver;
    private final LlmRouterAgent llmRouterAgent;

    public RouterAgent(DefaultValueResolver defaultValueResolver, LlmRouterAgent llmRouterAgent) {
        this.defaultValueResolver = defaultValueResolver;
        this.llmRouterAgent = llmRouterAgent;
    }

    public AgentPlan route(AgentExecuteRequest request) {
        AgentPlan plan = new AgentPlan();
        String text = request == null ? null : request.getText();
        if (!StringUtils.hasText(text)) {
            plan.setIntent(AgentConstants.INTENT_UNKNOWN);
            plan.getMissingFields().add("text");
            return plan;
        }

        try {
            log.info("Agent router start mode=llm textChars={}", text.length());
            AgentPlan llmPlan = llmRouterAgent.route(request);
            log.info(
                    "Agent router success mode=llm intent={} needConfirm={} missingFields={}",
                    llmPlan.getIntent(),
                    llmPlan.getNeedConfirm(),
                    llmPlan.getMissingFields()
            );
            if (isDelete(text) && !AgentConstants.INTENT_DELETE_EVENT.equals(llmPlan.getIntent())) {
                log.warn("LLM Router intent overridden by explicit delete command intent={}", llmPlan.getIntent());
                return buildDeletePlan(request);
            }
            if (isDelete(text)) {
                enrichDeleteResolveRange(llmPlan, request);
            }
            return llmPlan;
        } catch (RuntimeException e) {
            log.warn("LLM Router failed, fallback to rule router: {}", e.getMessage());
        }

        AgentPlan fallbackPlan;
        if (isDelete(text)) {
            fallbackPlan = buildDeletePlan(request);
            log.info("Agent router success mode=rule intent={}", fallbackPlan.getIntent());
            return fallbackPlan;
        }
        if (isUpdate(text)) {
            fallbackPlan = buildUpdatePlan(request);
            log.info("Agent router success mode=rule intent={}", fallbackPlan.getIntent());
            return fallbackPlan;
        }
        if (isQuery(text)) {
            fallbackPlan = buildQueryPlan(request);
            log.info("Agent router success mode=rule intent={}", fallbackPlan.getIntent());
            return fallbackPlan;
        }
        if (isCreate(text)) {
            fallbackPlan = buildCreatePlan(request);
            log.info("Agent router success mode=rule intent={}", fallbackPlan.getIntent());
            return fallbackPlan;
        }

        plan.setIntent(AgentConstants.INTENT_UNKNOWN);
        plan.getMissingFields().add("intent");
        return plan;
    }

    private AgentPlan buildCreatePlan(AgentExecuteRequest request) {
        LocalDateTime currentTime = parseCurrentTime(request.getCurrentTime());
        LocalDateTime startTime = parseStartTime(request.getText(), currentTime);
        String title = parseTitle(request.getText());

        AgentPlan plan = baseCalendarPlan(AgentConstants.INTENT_CREATE_EVENT, AgentConstants.ACTION_CREATE_EVENT, true);
        CreateEventRequest createRequest = new CreateEventRequest();
        createRequest.setUserId(defaultValueResolver.resolveUserId(request.getUserId()));
        createRequest.setTitle(title);
        createRequest.setStartTime(startTime);
        createRequest.setEndTime(null);
        createRequest.setReminderMinutes(null);
        createRequest.setSource("AGENT");
        createRequest.setOnlineMeeting(containsOnlineMeetingIntent(request.getText()));
        createRequest.setSmsReceiver(resolveSmsReceiver(request.getText()));
        plan.setCreateEventRequest(createRequest);

        if (!StringUtils.hasText(title)) {
            plan.getMissingFields().add("title");
        }
        if (startTime == null) {
            plan.getMissingFields().add("start_time");
        }
        plan.getSteps().add(AgentPlanStep.of(1, "RouterAgent", "ROUTE", "router.route"));
        plan.getSteps().add(AgentPlanStep.of(2, "CalendarAgent", "CHECK_CONFLICT", "calendar.conflict_check"));
        plan.getSteps().add(AgentPlanStep.of(3, "CalendarAgent", "CREATE_EVENT", "calendar.create"));
        return plan;
    }

    private AgentPlan buildQueryPlan(AgentExecuteRequest request) {
        LocalDateTime currentTime = parseCurrentTime(request.getCurrentTime());
        LocalDate date = parseDate(request.getText(), currentTime.toLocalDate());

        QueryEventRequest queryRequest = new QueryEventRequest();
        queryRequest.setUserId(defaultValueResolver.resolveUserId(request.getUserId()));
        if (request.getText().contains("下午")) {
            queryRequest.setStartTime(date.atTime(12, 0));
            queryRequest.setEndTime(date.atTime(18, 0));
        } else {
            queryRequest.setStartTime(date.atStartOfDay());
            queryRequest.setEndTime(date.plusDays(1).atStartOfDay());
        }

        AgentPlan plan = baseCalendarPlan(AgentConstants.INTENT_QUERY_EVENT, AgentConstants.ACTION_QUERY_EVENT, false);
        plan.setQueryEventRequest(queryRequest);
        plan.getSteps().add(AgentPlanStep.of(1, "RouterAgent", "ROUTE", "router.route"));
        plan.getSteps().add(AgentPlanStep.of(2, "CalendarAgent", "QUERY_EVENT", "calendar.query"));
        return plan;
    }

    private AgentPlan buildUpdatePlan(AgentExecuteRequest request) {
        LocalDateTime currentTime = parseCurrentTime(request.getCurrentTime());
        String[] parts = request.getText().split("(改到|改成|调整到|挪到)", 2);
        String targetText = parts[0];
        String updateText = parts.length > 1 ? parts[1] : "";
        LocalDate explicitTargetDate = parseExplicitDate(targetText, currentTime.toLocalDate());
        LocalDate targetDate = explicitTargetDate == null ? currentTime.toLocalDate() : explicitTargetDate;
        LocalTime targetTime = parseHour(targetText);
        LocalTime newTime = parseHour(updateText);
        if (!hasTimePeriod(updateText) && targetTime != null && newTime != null
                && targetTime.getHour() >= 12 && newTime.getHour() < 12) {
            newTime = newTime.plusHours(12);
        }
        LocalDate newDate = parseDate(updateText, targetDate);

        AgentPlan plan = baseCalendarPlan(AgentConstants.INTENT_UPDATE_EVENT, AgentConstants.ACTION_UPDATE_EVENT, true);
        EventResolveRequest resolveRequest = new EventResolveRequest();
        resolveRequest.setUserId(defaultValueResolver.resolveUserId(request.getUserId()));
        resolveRequest.setTitleKeyword(parseUpdateTitle(targetText));
        fillResolveRangeIfPresent(resolveRequest, explicitTargetDate != null, targetDate, targetTime);
        plan.setEventResolveRequest(resolveRequest);

        UpdateEventRequest updateRequest = new UpdateEventRequest();
        updateRequest.setUserId(defaultValueResolver.resolveUserId(request.getUserId()));
        LocalDateTime newStart = newTime == null ? null : newDate.atTime(newTime);
        updateRequest.setStartTime(newStart);
        updateRequest.setEndTime(defaultValueResolver.resolveEndTime(newStart, null));
        plan.setUpdateEventRequest(updateRequest);

        ensureResolveCriteria(plan);
        if (newStart == null) {
            plan.getMissingFields().add("start_time");
        }
        plan.getSteps().add(AgentPlanStep.of(1, "RouterAgent", "ROUTE", "router.route"));
        plan.getSteps().add(AgentPlanStep.of(2, "CalendarAgent", "RESOLVE_EVENT", "calendar.event.resolve"));
        plan.getSteps().add(AgentPlanStep.of(3, "CalendarAgent", "UPDATE_EVENT", "calendar.update"));
        return plan;
    }

    private AgentPlan buildDeletePlan(AgentExecuteRequest request) {
        LocalDateTime currentTime = parseCurrentTime(request.getCurrentTime());
        LocalDate explicitTargetDate = parseExplicitDate(request.getText(), currentTime.toLocalDate());
        LocalDate targetDate = explicitTargetDate == null ? currentTime.toLocalDate() : explicitTargetDate;
        LocalTime targetTime = parseHour(request.getText());

        AgentPlan plan = baseCalendarPlan(AgentConstants.INTENT_DELETE_EVENT, AgentConstants.ACTION_DELETE_EVENT, true);
        EventResolveRequest resolveRequest = new EventResolveRequest();
        resolveRequest.setUserId(defaultValueResolver.resolveUserId(request.getUserId()));
        resolveRequest.setTitleKeyword(parseDeleteTitle(request.getText()));
        fillResolveRangeIfPresent(resolveRequest, explicitTargetDate != null, targetDate, targetTime);
        plan.setEventResolveRequest(resolveRequest);
        ensureResolveCriteria(plan);
        plan.getSteps().add(AgentPlanStep.of(1, "RouterAgent", "ROUTE", "router.route"));
        plan.getSteps().add(AgentPlanStep.of(2, "CalendarAgent", "RESOLVE_EVENT", "calendar.event.resolve"));
        plan.getSteps().add(AgentPlanStep.of(3, "CalendarAgent", "DELETE_EVENT", "calendar.delete"));
        return plan;
    }

    private AgentPlan baseCalendarPlan(String intent, String actionType, Boolean needConfirm) {
        AgentPlan plan = new AgentPlan();
        plan.setIntent(intent);
        plan.setTargetAgent(AgentConstants.TARGET_CALENDAR_AGENT);
        plan.setActionType(actionType);
        plan.setNeedConfirm(needConfirm);
        return plan;
    }

    private boolean isQuery(String text) {
        return text.contains("有什么安排") || text.contains("查") || text.contains("查询") || text.endsWith("日程");
    }

    private boolean isCreate(String text) {
        if (text.contains("有什么安排")) {
            return false;
        }
        return text.contains("提醒") || text.contains("安排") || text.contains("创建") || text.contains("新建");
    }

    private boolean isUpdate(String text) {
        return text.contains("改到") || text.contains("改成") || text.contains("调整到") || text.contains("挪到");
    }

    private boolean isDelete(String text) {
        return text.contains("删除") || text.contains("取消日程") || text.contains("取消掉") || text.startsWith("取消");
    }

    private void fillResolveRange(EventResolveRequest request, LocalDate date, LocalTime time) {
        if (time == null) {
            request.setRangeStart(date.atStartOfDay());
            request.setRangeEnd(date.plusDays(1).atStartOfDay());
            return;
        }
        request.setRangeStart(date.atTime(time));
        request.setRangeEnd(date.atTime(time).plusHours(1));
    }

    private void fillResolveRangeIfPresent(EventResolveRequest request, boolean hasExplicitDate, LocalDate date, LocalTime time) {
        if (hasExplicitDate || time != null) {
            fillResolveRange(request, date, time);
        }
    }

    private void enrichDeleteResolveRange(AgentPlan plan, AgentExecuteRequest request) {
        if (!AgentConstants.INTENT_DELETE_EVENT.equals(plan.getIntent()) || plan.getEventResolveRequest() == null) {
            return;
        }
        LocalDateTime currentTime = parseCurrentTime(request.getCurrentTime());
        LocalDate explicitDate = parseExplicitDate(request.getText(), currentTime.toLocalDate());
        EventResolveRequest resolveRequest = plan.getEventResolveRequest();
        if (explicitDate != null && (resolveRequest.getRangeStart() == null || resolveRequest.getRangeEnd() == null)) {
            fillResolveRange(resolveRequest, explicitDate, parseHour(request.getText()));
            resolveRequest.setTitleKeyword(parseDeleteTitle(request.getText()));
            ensureResolveCriteria(plan);
        }
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

    private LocalDateTime parseCurrentTime(String currentTime) {
        if (StringUtils.hasText(currentTime)) {
            return LocalDateTime.parse(currentTime.trim(), DATE_TIME_FORMATTER);
        }
        return LocalDateTime.now();
    }

    private LocalDateTime parseStartTime(String text, LocalDateTime currentTime) {
        LocalDate date = parseDate(text, currentTime.toLocalDate());
        LocalTime time = parseHour(text);
        return time == null ? null : date.atTime(time);
    }

    private LocalDate parseDate(String text, LocalDate today) {
        LocalDate explicitDate = parseExplicitDate(text, today);
        return explicitDate == null ? today : explicitDate;
    }

    private LocalDate parseExplicitDate(String text, LocalDate today) {
        if (text.contains("后天")) {
            return today.plusDays(2);
        }
        if (text.contains("明天")) {
            return today.plusDays(1);
        }
        if (text.contains("今天")) {
            return today;
        }
        Matcher monthDayMatcher = MONTH_DAY_PATTERN.matcher(text);
        if (monthDayMatcher.find()) {
            int year = monthDayMatcher.group(1) == null ? today.getYear() : Integer.parseInt(monthDayMatcher.group(1));
            return safeDate(year, Integer.parseInt(monthDayMatcher.group(2)), Integer.parseInt(monthDayMatcher.group(3)));
        }
        Matcher dayMatcher = DAY_OF_MONTH_PATTERN.matcher(text);
        if (dayMatcher.find()) {
            return safeDate(today.getYear(), today.getMonthValue(), Integer.parseInt(dayMatcher.group(1)));
        }
        return null;
    }

    private LocalDate safeDate(int year, int month, int dayOfMonth) {
        try {
            return LocalDate.of(year, month, dayOfMonth);
        } catch (DateTimeException ignored) {
            return null;
        }
    }

    private LocalTime parseHour(String text) {
        Matcher matcher = HOUR_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String period = matcher.group(1);
        int hour = parseHourNumber(matcher.group(2));
        if (("下午".equals(period) || "晚上".equals(period)) && hour < 12) {
            hour += 12;
        }
        if ("中午".equals(period) && hour < 11) {
            hour += 12;
        }
        return LocalTime.of(hour, 0);
    }

    private boolean hasTimePeriod(String text) {
        return text.contains("凌晨")
                || text.contains("早上")
                || text.contains("上午")
                || text.contains("中午")
                || text.contains("下午")
                || text.contains("晚上");
    }

    private int parseHourNumber(String value) {
        if (value.matches("[0-9]{1,2}")) {
            return Integer.parseInt(value);
        }
        if ("十".equals(value)) {
            return 10;
        }
        if (value.startsWith("十")) {
            return 10 + chineseDigit(value.charAt(1));
        }
        if (value.endsWith("十")) {
            return chineseDigit(value.charAt(0)) * 10;
        }
        if (value.contains("十")) {
            return chineseDigit(value.charAt(0)) * 10 + chineseDigit(value.charAt(2));
        }
        return chineseDigit(value.charAt(0));
    }

    private int chineseDigit(char value) {
        switch (value) {
            case '一':
                return 1;
            case '二':
            case '两':
                return 2;
            case '三':
                return 3;
            case '四':
                return 4;
            case '五':
                return 5;
            case '六':
                return 6;
            case '七':
                return 7;
            case '八':
                return 8;
            case '九':
                return 9;
            default:
                return 0;
        }
    }

    private String parseTitle(String text) {
        String title = text
                .replaceAll(DATE_EXPRESSION_REGEX, "")
                .replaceAll("(凌晨|早上|上午|中午|下午|晚上)?([0-9]{1,2}|[一二两三四五六七八九十]{1,3})点", "")
                .replace("提醒我", "")
                .replace("帮我", "")
                .replace("创建", "")
                .replace("新建", "")
                .replace("安排", "")
                .replace("一个", "")
                .replace("日程", "")
                .replaceAll("[，。,.？?！!\\s]", "")
                .trim();
        if (title.startsWith("开") && title.length() > 1) {
            title = title.substring(1);
        }
        return title;
    }

    private String parseUpdateTitle(String text) {
        return normalizeEventTitle(text
                .replace("把", "")
                .replace("将", ""));
    }

    private String parseDeleteTitle(String text) {
        return normalizeEventTitle(text
                .replace("取消掉", "")
                .replace("取消日程", "")
                .replace("取消", "")
                .replace("删除", ""));
    }

    private String normalizeEventTitle(String text) {
        return text
                .replace("帮我", "")
                .replace("请", "")
                .replaceAll(DATE_EXPRESSION_REGEX, "")
                .replaceAll("(凌晨|早上|上午|中午|下午|晚上)?([0-9]{1,2}|[一二两三四五六七八九十]{1,3})点", "")
                .replace("的", "")
                .replace("日程", "")
                .replaceAll("[，。,.？?！!\\s]", "")
                .trim();
    }

    private boolean containsOnlineMeetingIntent(String text) {
        return StringUtils.hasText(text)
                && ((text.contains("线上") && text.contains("会")) || text.contains("会议链接"));
    }

    private String resolveSmsReceiver(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = SMS_RECEIVER_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
