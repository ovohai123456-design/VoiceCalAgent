package com.voice.agent.agent;

import com.voice.agent.model.dto.AgentCancelRequest;
import com.voice.agent.model.dto.AgentConfirmRequest;
import com.voice.agent.model.dto.AgentExecuteRequest;
import com.voice.agent.model.dto.AgentSelectSlotRequest;
import com.voice.agent.model.dto.AgentSelectEventRequest;
import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.dto.DeleteEventActionPayload;
import com.voice.agent.model.dto.UpdateEventActionPayload;
import com.voice.agent.model.dto.EventSelectionActionPayload;
import com.voice.agent.model.entity.CommandActionEntity;
import com.voice.agent.model.entity.CommandTaskEntity;
import com.voice.agent.model.entity.ExecutionLogEntity;
import com.voice.agent.model.entity.PendingConfirmationEntity;
import com.voice.agent.model.vo.AgentResponse;
import com.voice.agent.model.vo.CalendarEventVO;
import com.voice.agent.tool.GenericToolAgent;
import com.voice.agent.tool.ToolActionStep;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Agent 应用编排服务。
 *
 * <p>负责把 RouterAgent、领域 Agent、确认服务和 Workflow 串起来。
 * 这里不直接写日历表，真实业务动作交给 CalendarAgent/CalendarService。</p>
 */
@Service
public class AgentApplicationService {
    private static final String DEFAULT_SESSION_ID = "default_session";

    private final RouterAgent routerAgent;
    private final CalendarAgent calendarAgent;
    private final CommandActionService commandActionService;
    private final ConfirmService confirmService;
    private final CommandWorkflowService commandWorkflowService;
    private final DefaultValueResolver defaultValueResolver;
    private final ActionPlanBuilder actionPlanBuilder;
    private final GenericToolAgent genericToolAgent;

    @Value("${voicecal.confirm.auto-execute-safe-writes:true}")
    private Boolean autoExecuteSafeWrites = false;

    @Value("${voicecal.conversation.clarification-timeout-minutes:10}")
    private Integer clarificationTimeoutMinutes = 10;

    public AgentApplicationService(
            RouterAgent routerAgent,
            CalendarAgent calendarAgent,
            CommandActionService commandActionService,
            ConfirmService confirmService,
            CommandWorkflowService commandWorkflowService,
            DefaultValueResolver defaultValueResolver,
            ActionPlanBuilder actionPlanBuilder,
            GenericToolAgent genericToolAgent
    ) {
        this.routerAgent = routerAgent;
        this.calendarAgent = calendarAgent;
        this.commandActionService = commandActionService;
        this.confirmService = confirmService;
        this.commandWorkflowService = commandWorkflowService;
        this.defaultValueResolver = defaultValueResolver;
        this.actionPlanBuilder = actionPlanBuilder;
        this.genericToolAgent = genericToolAgent;
    }

    @Transactional
    public AgentResponse execute(AgentExecuteRequest request) {
        normalizeExecuteRequest(request);
        applyConversationContext(request);
        CommandTaskEntity task = commandWorkflowService.createTask(request);
        ExecutionLogEntity routeLog = null;

        try {
            routeLog = commandWorkflowService.addLog(
                    task.getTaskId(),
                    1,
                    "router.route",
                    "RouterAgent 路由任务",
                    "agent",
                    request
            );
            AgentPlan plan = routerAgent.route(request);
            if (plan.getCreateEventRequest() != null) {
                defaultValueResolver.applyCreatePreferences(plan.getCreateEventRequest());
            }
            commandWorkflowService.markLogSuccess(routeLog, plan);

            if (!plan.getMissingFields().isEmpty()) {
                String reply = buildClarifyText(plan);
                commandWorkflowService.markNeedClarification(task.getTaskId(), plan.getIntent(), reply);
                return clarifyResponse(task.getTaskId(), plan.getMissingFields(), reply);
            }

            if (AgentConstants.INTENT_CREATE_EVENT.equals(plan.getIntent())) {
                return prepareCreateEvent(task, plan);
            }
            if (AgentConstants.INTENT_QUERY_EVENT.equals(plan.getIntent())) {
                return executeQueryEvent(task, plan);
            }
            if (AgentConstants.INTENT_UPDATE_EVENT.equals(plan.getIntent())) {
                return prepareUpdateEvent(task, plan);
            }
            if (AgentConstants.INTENT_DELETE_EVENT.equals(plan.getIntent())) {
                return prepareDeleteEvent(task, plan);
            }

            String reply = "我暂时不能处理这个任务，你可以换一种说法。";
            commandWorkflowService.finishFailed(task.getTaskId(), AgentConstants.INTENT_UNKNOWN, reply, reply);
            return failedResponse(task.getTaskId(), reply);
        } catch (RuntimeException e) {
            String reply = "处理任务失败：" + e.getMessage();
            if (routeLog != null && AgentConstants.STATUS_RUNNING.equals(routeLog.getStatus())) {
                commandWorkflowService.markLogFailed(routeLog, e.getMessage());
            }
            commandWorkflowService.finishFailed(task.getTaskId(), AgentConstants.INTENT_UNKNOWN, e.getMessage(), reply);
            return failedResponse(task.getTaskId(), reply);
        }
    }

    @Transactional
    public AgentResponse confirm(AgentConfirmRequest request) {
        normalizeConfirmRequest(request);
        PendingConfirmationEntity confirmation = null;
        CommandActionEntity action = null;
        ExecutionLogEntity executeLog = null;
        try {
            confirmation = confirmService.getPendingConfirmation(
                    request.getUserId(),
                    request.getSessionId(),
                    request.getConfirmToken()
            );
            action = commandActionService.getAction(confirmation.getActionId());

            executeLog = commandWorkflowService.addLog(
                    action.getTaskId(),
                    4,
                    "calendar.create",
                    "CalendarAgent 执行确认动作",
                    "agent",
                    action
            );

            ActionExecution execution = executeAction(action);
            confirmService.markConfirmed(confirmation);
            commandActionService.markExecuted(action);
            commandWorkflowService.markLogSuccess(executeLog, execution.getData());
            commandWorkflowService.finishSuccess(action.getTaskId(), execution.getIntent(), execution.getReplyText(), execution.getReplyText());
            return successResponse(action.getTaskId(), execution);
        } catch (RuntimeException e) {
            if (executeLog != null) {
                commandWorkflowService.markLogFailed(executeLog, e.getMessage());
            }
            if (action != null) {
                commandActionService.markFailed(action, e.getMessage());
                commandWorkflowService.finishFailed(action.getTaskId(), intentForAction(action.getActionType()), e.getMessage(), "确认失败：" + e.getMessage());
                return failedResponse(action.getTaskId(), "确认失败：" + e.getMessage());
            }
            return failedResponse(null, "确认失败：" + e.getMessage());
        }
    }

    @Transactional
    public AgentResponse cancel(AgentCancelRequest request) {
        normalizeCancelRequest(request);
        try {
            PendingConfirmationEntity confirmation = confirmService.getPendingConfirmation(
                    request.getUserId(),
                    request.getSessionId(),
                    request.getConfirmToken()
            );
            CommandActionEntity action = commandActionService.getAction(confirmation.getActionId());
            confirmService.markCanceled(confirmation);
            commandActionService.markCanceled(action);
            commandWorkflowService.cancelTask(action.getTaskId(), "已取消该操作。");
            return baseResponse(true, action.getTaskId(), "已取消该操作。");
        } catch (RuntimeException e) {
            return failedResponse(null, "取消失败：" + e.getMessage());
        }
    }

    @Transactional
    public AgentResponse selectSlot(AgentSelectSlotRequest request) {
        normalizeSelectSlotRequest(request);
        PendingConfirmationEntity confirmation = null;
        CommandActionEntity action = null;
        ExecutionLogEntity executeLog = null;
        try {
            confirmation = confirmService.getPendingConfirmation(
                    request.getUserId(),
                    request.getSessionId(),
                    request.getConfirmToken()
            );
            action = commandActionService.getAction(confirmation.getActionId());
            if (!AgentConstants.ACTION_CREATE_EVENT.equals(action.getActionType())) {
                throw new IllegalStateException("当前操作不支持候选时间选择");
            }

            CreateEventRequest createRequest = commandActionService.readPayload(action, CreateEventRequest.class);
            CreateEventRequest selectedRequest = calendarAgent.selectSuggestedSlot(createRequest, request.getSlotIndex());
            commandActionService.updatePayload(action, selectedRequest);

            executeLog = commandWorkflowService.addLog(
                    action.getTaskId(),
                    4,
                    "calendar.create.selected_slot",
                    "CalendarAgent 使用选中的候选时间创建日程",
                    "agent",
                    selectedRequest
            );
            ActionExecution execution = executeAction(action);
            confirmService.markConfirmed(confirmation);
            commandActionService.markExecuted(action);
            commandWorkflowService.markLogSuccess(executeLog, execution.getData());
            commandWorkflowService.finishSuccess(action.getTaskId(), execution.getIntent(), execution.getReplyText(), execution.getReplyText());
            return successResponse(action.getTaskId(), execution);
        } catch (RuntimeException e) {
            if (executeLog != null) {
                commandWorkflowService.markLogFailed(executeLog, e.getMessage());
            }
            if (action != null) {
                commandActionService.markFailed(action, e.getMessage());
                commandWorkflowService.finishFailed(action.getTaskId(), AgentConstants.INTENT_CREATE_EVENT, e.getMessage(), "选择时间失败：" + e.getMessage());
                return failedResponse(action.getTaskId(), "选择时间失败：" + e.getMessage());
            }
            return failedResponse(null, "选择时间失败：" + e.getMessage());
        }
    }

    @Transactional
    public AgentResponse selectEvent(AgentSelectEventRequest request) {
        normalizeSelectEventRequest(request);
        PendingConfirmationEntity confirmation = confirmService.getPendingConfirmation(
                request.getUserId(), request.getSessionId(), request.getConfirmToken()
        );
        CommandActionEntity action = commandActionService.getAction(confirmation.getActionId());
        EventSelectionActionPayload selection = commandActionService.readPayload(action, EventSelectionActionPayload.class);
        if (request.getCandidateIndex() < 0 || request.getCandidateIndex() >= selection.getCandidates().size()) {
            throw new IllegalArgumentException("候选日程不存在，请重新发起操作");
        }
        CalendarEventVO selected = selection.getCandidates().get(request.getCandidateIndex());
        if (AgentConstants.ACTION_UPDATE_EVENT.equals(action.getActionType())) {
            CalendarAgent.PreparedUpdateAction prepared = calendarAgent.prepareUpdateAction(
                    selected, selection.getUpdateRequest(), selection.getSourceTaskId()
            );
            commandActionService.updatePayload(action, prepared.getPayload());
            if (shouldAutoExecuteSafeWrites()) {
                ActionExecution execution = executeAction(action);
                confirmService.markConfirmed(confirmation);
                commandActionService.markExecuted(action);
                commandWorkflowService.finishSuccess(action.getTaskId(), execution.getIntent(), execution.getReplyText(), execution.getReplyText());
                return successResponse(action.getTaskId(), execution);
            }
            return eventSelectedResponse(action.getTaskId(), request.getConfirmToken(), prepared.getReplyText());
        }
        if (AgentConstants.ACTION_DELETE_EVENT.equals(action.getActionType())) {
            CalendarAgent.PreparedDeleteAction prepared = calendarAgent.prepareDeleteAction(selected);
            commandActionService.updatePayload(action, prepared.getPayload());
            return eventSelectedResponse(action.getTaskId(), request.getConfirmToken(), prepared.getReplyText());
        }
        throw new IllegalStateException("当前操作不支持候选日程选择");
    }

    private AgentResponse prepareCreateEvent(CommandTaskEntity task, AgentPlan plan) {
        ExecutionLogEntity calendarLog = commandWorkflowService.addLog(
                task.getTaskId(),
                2,
                "calendar.conflict_check",
                "CalendarAgent 检查冲突并准备创建",
                "agent",
                plan.getCreateEventRequest()
        );

        CalendarAgent.PreparedCreateAction prepared;
        try {
            prepared = calendarAgent.prepareCreateAction(
                    plan.getCreateEventRequest(),
                    task.getTaskId()
            );
            commandWorkflowService.markLogSuccess(calendarLog, prepared);
        } catch (RuntimeException e) {
            commandWorkflowService.markLogFailed(calendarLog, e.getMessage());
            throw e;
        }

        CommandActionEntity action = commandActionService.createAction(
                task.getTaskId(),
                AgentConstants.ACTION_CREATE_EVENT,
                prepared.getCreateEventRequest()
        );
        if (Boolean.TRUE.equals(prepared.getRequiresSlotSelection())) {
            return createPendingResponse(task, action, prepared.getReplyText(), prepared);
        }
        if (shouldAutoExecuteSafeWrites()) {
            return executePreparedAction(task, action, 3, "calendar.create", "CalendarAgent 自动创建日程");
        }
        commandActionService.markWaitingConfirm(action);

        PendingConfirmationEntity confirmation = confirmService.createPendingConfirmation(
                task.getUserId(),
                task.getSessionId(),
                action.getActionId()
        );

        ExecutionLogEntity confirmationLog = commandWorkflowService.addLog(
                task.getTaskId(),
                3,
                "pending_confirmation.create",
                "创建待确认记录",
                "agent",
                confirmation
        );
        commandWorkflowService.markLogSuccess(confirmationLog, action.getActionId());

        commandWorkflowService.markWaitingConfirm(
                task.getTaskId(),
                AgentConstants.INTENT_CREATE_EVENT,
                prepared.getReplyText(),
                prepared.getReplyText()
        );

        AgentResponse response = baseResponse(true, task.getTaskId(), prepared.getReplyText());
        response.setNeedConfirm(true);
        response.setConfirmToken(confirmation.getConfirmToken());
        response.setData(prepared);
        return response;
    }

    private AgentResponse prepareUpdateEvent(CommandTaskEntity task, AgentPlan plan) {
        ExecutionLogEntity prepareLog = commandWorkflowService.addLog(
                task.getTaskId(),
                2,
                "calendar.update.prepare",
                "CalendarAgent 定位日程并检查修改冲突",
                "agent",
                plan.getEventResolveRequest()
        );
        CalendarAgent.PreparedUpdateAction prepared;
        try {
            prepared = calendarAgent.prepareUpdateAction(
                    plan.getEventResolveRequest(),
                    plan.getUpdateEventRequest(),
                    task.getTaskId()
            );
            commandWorkflowService.markLogSuccess(prepareLog, prepared);
        } catch (AmbiguousEventException e) {
            commandWorkflowService.markLogSuccess(prepareLog, e.getCandidates());
            return createEventSelectionResponse(task, AgentConstants.ACTION_UPDATE_EVENT, plan.getUpdateEventRequest(), e.getCandidates());
        } catch (RuntimeException e) {
            commandWorkflowService.markLogFailed(prepareLog, e.getMessage());
            throw e;
        }

        CommandActionEntity action = commandActionService.createAction(
                task.getTaskId(),
                AgentConstants.ACTION_UPDATE_EVENT,
                prepared.getPayload()
        );
        if (shouldAutoExecuteSafeWrites()) {
            return executePreparedAction(task, action, 3, "calendar.update", "CalendarAgent 自动修改日程");
        }
        return createPendingResponse(task, action, prepared.getReplyText());
    }

    private AgentResponse prepareDeleteEvent(CommandTaskEntity task, AgentPlan plan) {
        ExecutionLogEntity prepareLog = commandWorkflowService.addLog(
                task.getTaskId(),
                2,
                "calendar.delete.prepare",
                "CalendarAgent 定位待删除日程",
                "agent",
                plan.getEventResolveRequest()
        );
        CalendarAgent.PreparedDeleteAction prepared;
        try {
            prepared = calendarAgent.prepareDeleteAction(plan.getEventResolveRequest());
            commandWorkflowService.markLogSuccess(prepareLog, prepared);
        } catch (AmbiguousEventException e) {
            commandWorkflowService.markLogSuccess(prepareLog, e.getCandidates());
            return createEventSelectionResponse(task, AgentConstants.ACTION_DELETE_EVENT, null, e.getCandidates());
        } catch (RuntimeException e) {
            commandWorkflowService.markLogFailed(prepareLog, e.getMessage());
            throw e;
        }

        CommandActionEntity action = commandActionService.createAction(
                task.getTaskId(),
                AgentConstants.ACTION_DELETE_EVENT,
                prepared.getPayload()
        );
        return createPendingResponse(task, action, prepared.getReplyText());
    }

    private AgentResponse createPendingResponse(CommandTaskEntity task, CommandActionEntity action, String replyText) {
        return createPendingResponse(task, action, replyText, null);
    }

    private AgentResponse createPendingResponse(CommandTaskEntity task, CommandActionEntity action, String replyText, Object data) {
        commandActionService.markWaitingConfirm(action);
        PendingConfirmationEntity confirmation = confirmService.createPendingConfirmation(
                task.getUserId(),
                task.getSessionId(),
                action.getActionId()
        );
        ExecutionLogEntity confirmationLog = commandWorkflowService.addLog(
                task.getTaskId(),
                3,
                "pending_confirmation.create",
                "创建待确认记录",
                "agent",
                confirmation
        );
        commandWorkflowService.markLogSuccess(confirmationLog, action.getActionId());
        commandWorkflowService.markWaitingConfirm(task.getTaskId(), intentForAction(action.getActionType()), replyText, replyText);

        AgentResponse response = baseResponse(true, task.getTaskId(), replyText);
        response.setNeedConfirm(true);
        response.setConfirmToken(confirmation.getConfirmToken());
        response.setData(data);
        return response;
    }

    private AgentResponse createEventSelectionResponse(
            CommandTaskEntity task,
            String actionType,
            com.voice.agent.model.dto.UpdateEventRequest updateRequest,
            List<CalendarEventVO> candidates
    ) {
        EventSelectionActionPayload payload = new EventSelectionActionPayload();
        payload.setActionType(actionType);
        payload.setSourceTaskId(task.getTaskId());
        payload.setUpdateRequest(updateRequest);
        payload.setCandidates(candidates);
        CommandActionEntity action = commandActionService.createAction(task.getTaskId(), actionType, payload);
        AgentResponse response = createPendingResponse(task, action, "找到多个匹配日程，请选择要操作的日程", payload);
        response.setNeedEventSelection(true);
        return response;
    }

    private AgentResponse eventSelectedResponse(String taskId, String confirmToken, String replyText) {
        AgentResponse response = baseResponse(true, taskId, replyText);
        response.setNeedConfirm(true);
        response.setConfirmToken(confirmToken);
        response.setNeedEventSelection(false);
        return response;
    }

    private AgentResponse executePreparedAction(
            CommandTaskEntity task,
            CommandActionEntity action,
            Integer stepOrder,
            String skillId,
            String stepName
    ) {
        ExecutionLogEntity executeLog = commandWorkflowService.addLog(
                task.getTaskId(),
                stepOrder,
                skillId,
                stepName,
                "agent",
                action
        );
        try {
            ActionExecution execution = executeAction(action);
            commandActionService.markExecuted(action);
            commandWorkflowService.markLogSuccess(executeLog, execution.getData());
            commandWorkflowService.finishSuccess(task.getTaskId(), execution.getIntent(), execution.getReplyText(), execution.getReplyText());
            return successResponse(task.getTaskId(), execution);
        } catch (RuntimeException e) {
            commandActionService.markFailed(action, e.getMessage());
            commandWorkflowService.markLogFailed(executeLog, e.getMessage());
            throw e;
        }
    }

    private ActionExecution executeAction(CommandActionEntity action) {
        if (AgentConstants.ACTION_CREATE_EVENT.equals(action.getActionType())) {
            CreateEventRequest request = commandActionService.readPayload(action, CreateEventRequest.class);
            GenericToolAgent.ToolExecutionSummary beforeCreate = executeToolSteps(
                    action.getTaskId(),
                    actionPlanBuilder.buildBeforeCalendarCreate(request)
            );
            applyMeetingUrl(request, beforeCreate);
            CalendarEventVO event = calendarAgent.executeCreate(request);
            executeToolSteps(action.getTaskId(), actionPlanBuilder.buildAfterCalendarCreate(request, event));
            return new ActionExecution(AgentConstants.INTENT_CREATE_EVENT, event, calendarAgent.buildCreateSuccessText(event));
        }
        if (AgentConstants.ACTION_UPDATE_EVENT.equals(action.getActionType())) {
            UpdateEventActionPayload payload = commandActionService.readPayload(action, UpdateEventActionPayload.class);
            CalendarEventVO event = calendarAgent.executeUpdate(payload);
            return new ActionExecution(AgentConstants.INTENT_UPDATE_EVENT, event, calendarAgent.buildUpdateSuccessText(event));
        }
        if (AgentConstants.ACTION_DELETE_EVENT.equals(action.getActionType())) {
            DeleteEventActionPayload payload = commandActionService.readPayload(action, DeleteEventActionPayload.class);
            Boolean deleted = calendarAgent.executeDelete(payload);
            return new ActionExecution(AgentConstants.INTENT_DELETE_EVENT, deleted, calendarAgent.buildDeleteSuccessText(payload));
        }
        throw new IllegalStateException("不支持的确认动作：" + action.getActionType());
    }

    private GenericToolAgent.ToolExecutionSummary executeToolSteps(String taskId, List<ToolActionStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return new GenericToolAgent.ToolExecutionSummary();
        }
        return genericToolAgent.execute(taskId, steps);
    }

    private void applyMeetingUrl(CreateEventRequest request, GenericToolAgent.ToolExecutionSummary summary) {
        Object meeting = summary.getContext().get("meeting");
        if (!(meeting instanceof Map)) {
            return;
        }
        Object meetingUrl = ((Map<?, ?>) meeting).get("meeting_url");
        if (meetingUrl != null) {
            request.setMeetingUrl(String.valueOf(meetingUrl));
        }
    }

    private AgentResponse successResponse(String taskId, ActionExecution execution) {
        AgentResponse response = baseResponse(true, taskId, execution.getReplyText());
        response.setData(execution.getData());
        return response;
    }

    private String intentForAction(String actionType) {
        if (AgentConstants.ACTION_CREATE_EVENT.equals(actionType)) {
            return AgentConstants.INTENT_CREATE_EVENT;
        }
        if (AgentConstants.ACTION_UPDATE_EVENT.equals(actionType)) {
            return AgentConstants.INTENT_UPDATE_EVENT;
        }
        if (AgentConstants.ACTION_DELETE_EVENT.equals(actionType)) {
            return AgentConstants.INTENT_DELETE_EVENT;
        }
        if (AgentConstants.ACTION_QUERY_EVENT.equals(actionType)) {
            return AgentConstants.INTENT_QUERY_EVENT;
        }
        return AgentConstants.INTENT_UNKNOWN;
    }

    private boolean shouldAutoExecuteSafeWrites() {
        return Boolean.TRUE.equals(autoExecuteSafeWrites);
    }

    private AgentResponse executeQueryEvent(CommandTaskEntity task, AgentPlan plan) {
        CommandActionEntity action = commandActionService.createAction(
                task.getTaskId(),
                AgentConstants.ACTION_QUERY_EVENT,
                plan.getQueryEventRequest()
        );
        ExecutionLogEntity queryLog = commandWorkflowService.addLog(
                task.getTaskId(),
                2,
                "calendar.query",
                "CalendarAgent 查询日程",
                "agent",
                plan.getQueryEventRequest()
        );
        List<CalendarEventVO> events;
        try {
            events = calendarAgent.query(plan.getQueryEventRequest());
            commandActionService.markExecuted(action);
            commandWorkflowService.markLogSuccess(queryLog, events);
        } catch (RuntimeException e) {
            commandActionService.markFailed(action, e.getMessage());
            commandWorkflowService.markLogFailed(queryLog, e.getMessage());
            throw e;
        }

        String reply = calendarAgent.buildQueryReply(events);
        commandWorkflowService.finishSuccess(task.getTaskId(), AgentConstants.INTENT_QUERY_EVENT, reply, reply);

        AgentResponse response = baseResponse(true, task.getTaskId(), reply);
        response.setData(events);
        return response;
    }

    private String buildClarifyText(AgentPlan plan) {
        if (plan.getMissingFields().contains("start_time")) {
            return "你想安排在什么时间？";
        }
        if (plan.getMissingFields().contains("title")) {
            return "你想创建什么日程？";
        }
        return "我没有理解清楚，你可以换一种说法吗？";
    }

    private void normalizeExecuteRequest(AgentExecuteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        request.setUserId(defaultValueResolver.resolveUserId(request.getUserId()));
        if (!StringUtils.hasText(request.getSessionId())) {
            request.setSessionId(DEFAULT_SESSION_ID);
        }
        if (!StringUtils.hasText(request.getInputType())) {
            request.setInputType("TEXT");
        }
    }

    private void applyConversationContext(AgentExecuteRequest request) {
        CommandTaskEntity clarification = commandWorkflowService.findLatestNeedClarification(
                request.getUserId(),
                request.getSessionId(),
                clarificationTimeoutMinutes
        );
        if (clarification == null || !shouldMergeClarification(request.getText())) {
            return;
        }
        request.setText(clarification.getInputText() + " " + request.getText());
        commandWorkflowService.markClarificationContinued(clarification.getTaskId());
    }

    private boolean shouldMergeClarification(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return !text.contains("安排")
                && !text.contains("创建")
                && !text.contains("新建")
                && !text.contains("查询")
                && !text.contains("有什么安排")
                && !text.contains("删除")
                && !text.contains("取消")
                && !text.contains("改到")
                && !text.contains("改成")
                && !text.contains("调整到")
                && !text.contains("挪到");
    }

    private void normalizeConfirmRequest(AgentConfirmRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        request.setUserId(defaultValueResolver.resolveUserId(request.getUserId()));
        if (!StringUtils.hasText(request.getSessionId())) {
            request.setSessionId(DEFAULT_SESSION_ID);
        }
    }

    private void normalizeCancelRequest(AgentCancelRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        request.setUserId(defaultValueResolver.resolveUserId(request.getUserId()));
        if (!StringUtils.hasText(request.getSessionId())) {
            request.setSessionId(DEFAULT_SESSION_ID);
        }
    }

    private void normalizeSelectSlotRequest(AgentSelectSlotRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        request.setUserId(defaultValueResolver.resolveUserId(request.getUserId()));
        if (!StringUtils.hasText(request.getSessionId())) {
            request.setSessionId(DEFAULT_SESSION_ID);
        }
        if (!StringUtils.hasText(request.getConfirmToken())) {
            throw new IllegalArgumentException("confirmToken 不能为空");
        }
        if (request.getSlotIndex() == null) {
            throw new IllegalArgumentException("slotIndex 不能为空");
        }
    }

    private void normalizeSelectEventRequest(AgentSelectEventRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        request.setUserId(defaultValueResolver.resolveUserId(request.getUserId()));
        if (!StringUtils.hasText(request.getSessionId())) {
            request.setSessionId(DEFAULT_SESSION_ID);
        }
        if (!StringUtils.hasText(request.getConfirmToken()) || request.getCandidateIndex() == null) {
            throw new IllegalArgumentException("confirmToken 和 candidateIndex 不能为空");
        }
    }

    private AgentResponse clarifyResponse(String taskId, List<String> missingFields, String reply) {
        AgentResponse response = baseResponse(false, taskId, reply);
        response.setNeedClarify(true);
        response.setMissingFields(missingFields);
        return response;
    }

    private AgentResponse failedResponse(String taskId, String reply) {
        AgentResponse response = baseResponse(false, taskId, reply);
        response.setNeedClarify(false);
        response.setNeedEventSelection(false);
        return response;
    }

    private AgentResponse baseResponse(Boolean success, String taskId, String reply) {
        AgentResponse response = new AgentResponse();
        response.setSuccess(success);
        response.setTaskId(taskId);
        response.setNeedConfirm(false);
        response.setNeedClarify(false);
        response.setReplyText(reply);
        response.setSpeakText(reply);
        return response;
    }

    private static class ActionExecution {
        private final String intent;
        private final Object data;
        private final String replyText;

        private ActionExecution(String intent, Object data, String replyText) {
            this.intent = intent;
            this.data = data;
            this.replyText = replyText;
        }

        public String getIntent() {
            return intent;
        }

        public Object getData() {
            return data;
        }

        public String getReplyText() {
            return replyText;
        }
    }
}
