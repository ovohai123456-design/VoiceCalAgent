package com.voice.agent.model.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM RouterAgent 的结构化输出 DTO。
 *
 * <p>字段刻意和提示词 JSON 对齐，先做输入校验和转换，再进入后端 AgentPlan 执行模型。</p>
 */
public class RouterPlanResponse {
    private String intent;
    private String targetAgent;
    private String actionType;
    private Boolean needConfirm;
    private RouterSlots slots;
    private List<String> missingFields = new ArrayList<>();
    private List<RouterPlanStepDTO> steps = new ArrayList<>();

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

    public RouterSlots getSlots() {
        return slots;
    }

    public void setSlots(RouterSlots slots) {
        this.slots = slots;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }

    public void setMissingFields(List<String> missingFields) {
        this.missingFields = missingFields;
    }

    public List<RouterPlanStepDTO> getSteps() {
        return steps;
    }

    public void setSteps(List<RouterPlanStepDTO> steps) {
        this.steps = steps;
    }
}
