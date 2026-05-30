package com.voice.agent.tool;

import java.util.LinkedHashMap;
import java.util.Map;

public class ToolActionStep {
    private Integer stepOrder;
    private String skillId;
    private String outputAlias;
    private String onFailure = "STOP";
    private Map<String, Object> arguments = new LinkedHashMap<>();

    public static ToolActionStep of(Integer stepOrder, String skillId, String outputAlias, Map<String, Object> arguments) {
        ToolActionStep step = new ToolActionStep();
        step.setStepOrder(stepOrder);
        step.setSkillId(skillId);
        step.setOutputAlias(outputAlias);
        step.setArguments(arguments);
        return step;
    }

    public Integer getStepOrder() {
        return stepOrder;
    }

    public void setStepOrder(Integer stepOrder) {
        this.stepOrder = stepOrder;
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public String getOutputAlias() {
        return outputAlias;
    }

    public void setOutputAlias(String outputAlias) {
        this.outputAlias = outputAlias;
    }

    public String getOnFailure() {
        return onFailure;
    }

    public void setOnFailure(String onFailure) {
        this.onFailure = onFailure;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }
}
