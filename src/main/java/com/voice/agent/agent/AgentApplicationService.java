package com.voice.agent.agent;

import com.voice.agent.model.dto.AgentCancelRequest;
import com.voice.agent.model.dto.AgentConfirmRequest;
import com.voice.agent.model.dto.AgentExecuteRequest;
import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.entity.AgentStepEntity;
import com.voice.agent.model.entity.AgentTaskEntity;
import com.voice.agent.model.entity.PendingActionEntity;
import com.voice.agent.model.vo.AgentResponse;
import com.voice.agent.model.vo.CalendarEventVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

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
    private final ConfirmService confirmService;
    private final WorkflowService workflowService;
    private final DefaultValueResolver defaultValueResolver;

    public AgentApplicationService(
            RouterAgent routerAgent,
            CalendarAgent calendarAgent,
            ConfirmService confirmService,
            WorkflowService workflowService,
            DefaultValueResolver defaultValueResolver
    ) {
        this.routerAgent = routerAgent;
        this.calendarAgent = calendarAgent;
        this.confirmService = confirmService;
        this.workflowService = workflowService;
        this.defaultValueResolver = defaultValueResolver;
    }

    @Transactional
    public AgentResponse execute(AgentExecuteRequest request) {
        normalizeExecuteRequest(request);
        AgentTaskEntity task = workflowService.createTask(request);

        try {
            AgentStepEntity routeStep = workflowService.addStep(
                    task.getTaskId(),
                    1,
                    "router.route",
                    "RouterAgent 路由任务",
                    "agent",
                    request
            );
            AgentPlan plan = routerAgent.route(request);
            workflowService.markStepSuccess(routeStep, plan);

            if (!plan.getMissingFields().isEmpty()) {
                String reply = buildClarifyText(plan);
                workflowService.finishFailed(task.getTaskId(), plan.getIntent(), reply, reply);
                return clarifyResponse(task.getTaskId(), plan.getMissingFields(), reply);
            }

            if (AgentConstants.INTENT_CREATE_EVENT.equals(plan.getIntent())) {
                return prepareCreateEvent(task, plan);
            }
            if (AgentConstants.INTENT_QUERY_EVENT.equals(plan.getIntent())) {
                return executeQueryEvent(task, plan);
            }

            String reply = "我暂时不能处理这个任务，你可以换一种说法。";
            workflowService.finishFailed(task.getTaskId(), AgentConstants.INTENT_UNKNOWN, reply, reply);
            return failedResponse(task.getTaskId(), reply);
        } catch (RuntimeException e) {
            String reply = "处理任务失败：" + e.getMessage();
            workflowService.finishFailed(task.getTaskId(), AgentConstants.INTENT_UNKNOWN, e.getMessage(), reply);
            return failedResponse(task.getTaskId(), reply);
        }
    }

    @Transactional
    public AgentResponse confirm(AgentConfirmRequest request) {
        normalizeConfirmRequest(request);
        PendingActionEntity pendingAction = null;
        AgentStepEntity executeStep = null;
        try {
            pendingAction = confirmService.getPendingAction(
                    request.getUserId(),
                    request.getSessionId(),
                    request.getConfirmToken()
            );

            executeStep = workflowService.addStep(
                    pendingAction.getTaskId(),
                    99,
                    "calendar.create",
                    "CalendarAgent 执行确认动作",
                    "agent",
                    pendingAction
            );

            if (AgentConstants.ACTION_CREATE_EVENT.equals(pendingAction.getActionType())) {
                CreateEventRequest createRequest = confirmService.readPayload(pendingAction, CreateEventRequest.class);
                CalendarEventVO event = calendarAgent.executeCreate(createRequest);
                confirmService.markExecuted(pendingAction);

                String reply = calendarAgent.buildCreateSuccessText(event);
                workflowService.markStepSuccess(executeStep, event);
                workflowService.finishSuccess(pendingAction.getTaskId(), AgentConstants.INTENT_CREATE_EVENT, reply, reply);

                AgentResponse response = baseResponse(true, pendingAction.getTaskId(), reply);
                response.setData(event);
                return response;
            }

            throw new IllegalStateException("不支持的确认动作：" + pendingAction.getActionType());
        } catch (RuntimeException e) {
            if (executeStep != null) {
                workflowService.markStepFailed(executeStep, e.getMessage());
            }
            if (pendingAction != null) {
                workflowService.finishFailed(pendingAction.getTaskId(), AgentConstants.INTENT_CREATE_EVENT, e.getMessage(), "确认失败：" + e.getMessage());
                return failedResponse(pendingAction.getTaskId(), "确认失败：" + e.getMessage());
            }
            return failedResponse(null, "确认失败：" + e.getMessage());
        }
    }

    @Transactional
    public AgentResponse cancel(AgentCancelRequest request) {
        normalizeCancelRequest(request);
        try {
            PendingActionEntity pendingAction = confirmService.getPendingAction(
                    request.getUserId(),
                    request.getSessionId(),
                    request.getConfirmToken()
            );
            confirmService.markCanceled(pendingAction);
            workflowService.cancelTask(pendingAction.getTaskId(), "已取消该操作。");
            return baseResponse(true, pendingAction.getTaskId(), "已取消该操作。");
        } catch (RuntimeException e) {
            return failedResponse(null, "取消失败：" + e.getMessage());
        }
    }

    private AgentResponse prepareCreateEvent(AgentTaskEntity task, AgentPlan plan) {
        AgentStepEntity calendarStep = workflowService.addStep(
                task.getTaskId(),
                2,
                "calendar.conflict_check",
                "CalendarAgent 检查冲突并准备创建",
                "agent",
                plan.getCreateEventRequest()
        );

        CalendarAgent.PreparedCreateAction prepared = calendarAgent.prepareCreateAction(
                plan.getCreateEventRequest(),
                task.getTaskId()
        );
        workflowService.markStepSuccess(calendarStep, prepared);

        PendingActionEntity pendingAction = confirmService.createPendingAction(
                task.getUserId(),
                task.getSessionId(),
                task.getTaskId(),
                AgentConstants.ACTION_CREATE_EVENT,
                prepared.getCreateEventRequest()
        );

        AgentStepEntity pendingStep = workflowService.addStep(
                task.getTaskId(),
                3,
                "pending_action.create",
                "创建待确认操作",
                "agent",
                prepared.getCreateEventRequest()
        );
        workflowService.markStepSuccess(pendingStep, pendingAction);

        workflowService.markWaitingConfirm(
                task.getTaskId(),
                AgentConstants.INTENT_CREATE_EVENT,
                pendingAction.getConfirmToken(),
                prepared.getReplyText(),
                prepared.getReplyText()
        );

        AgentResponse response = baseResponse(true, task.getTaskId(), prepared.getReplyText());
        response.setNeedConfirm(true);
        response.setConfirmToken(pendingAction.getConfirmToken());
        response.setData(prepared);
        return response;
    }

    private AgentResponse executeQueryEvent(AgentTaskEntity task, AgentPlan plan) {
        AgentStepEntity queryStep = workflowService.addStep(
                task.getTaskId(),
                2,
                "calendar.query",
                "CalendarAgent 查询日程",
                "agent",
                plan.getQueryEventRequest()
        );
        List<CalendarEventVO> events = calendarAgent.query(plan.getQueryEventRequest());
        workflowService.markStepSuccess(queryStep, events);

        String reply = calendarAgent.buildQueryReply(events);
        workflowService.finishSuccess(task.getTaskId(), AgentConstants.INTENT_QUERY_EVENT, reply, reply);

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

    private AgentResponse clarifyResponse(String taskId, List<String> missingFields, String reply) {
        AgentResponse response = baseResponse(false, taskId, reply);
        response.setNeedClarify(true);
        response.setMissingFields(missingFields);
        return response;
    }

    private AgentResponse failedResponse(String taskId, String reply) {
        AgentResponse response = baseResponse(false, taskId, reply);
        response.setNeedClarify(false);
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
}
