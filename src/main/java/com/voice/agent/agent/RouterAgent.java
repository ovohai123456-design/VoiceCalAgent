package com.voice.agent.agent;

import com.voice.agent.model.dto.AgentExecuteRequest;
import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.dto.QueryEventRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
            return llmRouterAgent.route(request);
        } catch (RuntimeException e) {
            log.warn("LLM Router failed, fallback to rule router: {}", e.getMessage());
        }

        if (isQuery(text)) {
            return buildQueryPlan(request);
        }
        if (isCreate(text)) {
            return buildCreatePlan(request);
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
        createRequest.setEndTime(defaultValueResolver.resolveEndTime(startTime, null));
        createRequest.setReminderMinutes(defaultValueResolver.resolveReminderMinutes(null));
        createRequest.setSource("AGENT");
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
        if (text.contains("后天")) {
            return today.plusDays(2);
        }
        if (text.contains("明天")) {
            return today.plusDays(1);
        }
        return today;
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
        return switch (value) {
            case '一' -> 1;
            case '二', '两' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> 0;
        };
    }

    private String parseTitle(String text) {
        String title = text
                .replaceAll("(今天|明天|后天)", "")
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
}
