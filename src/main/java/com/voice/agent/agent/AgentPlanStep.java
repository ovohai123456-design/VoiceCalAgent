package com.voice.agent.agent;

/**
 * ActionPlan 中的一步，用于 Workflow 展示和后续通用 Tool Agent 执行。
 */
public class AgentPlanStep {
    private Integer stepOrder;
    private String agentName;
    private String action;
    private String skillId;

    public static AgentPlanStep of(Integer stepOrder, String agentName, String action, String skillId) {
        AgentPlanStep step = new AgentPlanStep();
        step.setStepOrder(stepOrder);
        step.setAgentName(agentName);
        step.setAction(action);
        step.setSkillId(skillId);
        return step;
    }

    public Integer getStepOrder() {
        return stepOrder;
    }

    public void setStepOrder(Integer stepOrder) {
        this.stepOrder = stepOrder;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }
}
