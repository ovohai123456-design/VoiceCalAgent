package com.voice.agent.agent;

import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.dto.EventResolveRequest;
import com.voice.agent.model.dto.QueryEventRequest;
import com.voice.agent.model.dto.UpdateEventRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * RouterAgent 输出的结构化计划。
 *
 * <p>第一版先用 Java 对象承载计划，后续接 LLM 时可以让模型直接生成同结构 JSON。</p>
 */
public class AgentPlan {
    private String intent;
    private String targetAgent;
    private String actionType;
    private Boolean needConfirm;
    private CreateEventRequest createEventRequest;
    private QueryEventRequest queryEventRequest;
    private EventResolveRequest eventResolveRequest;
    private UpdateEventRequest updateEventRequest;
    private List<String> missingFields = new ArrayList<>();
    private List<AgentPlanStep> steps = new ArrayList<>();

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getTargetAgent() {
        return targetAgent;
    }

    public void setTargetAgent(String targetAgent) {
        this.targetAgent = targetAgent;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public Boolean getNeedConfirm() {
        return needConfirm;
    }

    public void setNeedConfirm(Boolean needConfirm) {
        this.needConfirm = needConfirm;
    }

    public CreateEventRequest getCreateEventRequest() {
        return createEventRequest;
    }

    public void setCreateEventRequest(CreateEventRequest createEventRequest) {
        this.createEventRequest = createEventRequest;
    }

    public QueryEventRequest getQueryEventRequest() {
        return queryEventRequest;
    }

    public void setQueryEventRequest(QueryEventRequest queryEventRequest) {
        this.queryEventRequest = queryEventRequest;
    }

    public EventResolveRequest getEventResolveRequest() {
        return eventResolveRequest;
    }

    public void setEventResolveRequest(EventResolveRequest eventResolveRequest) {
        this.eventResolveRequest = eventResolveRequest;
    }

    public UpdateEventRequest getUpdateEventRequest() {
        return updateEventRequest;
    }

    public void setUpdateEventRequest(UpdateEventRequest updateEventRequest) {
        this.updateEventRequest = updateEventRequest;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }

    public void setMissingFields(List<String> missingFields) {
        this.missingFields = missingFields;
    }

    public List<AgentPlanStep> getSteps() {
        return steps;
    }

    public void setSteps(List<AgentPlanStep> steps) {
        this.steps = steps;
    }
}
