package com.voice.agent.tool;

public class ToolExecutionResult {
    private String skillId;
    private Boolean success;
    private Object data;
    private String errorMessage;

    public static ToolExecutionResult success(String skillId, Object data) {
        ToolExecutionResult result = new ToolExecutionResult();
        result.setSkillId(skillId);
        result.setSuccess(true);
        result.setData(data);
        return result;
    }

    public static ToolExecutionResult failed(String skillId, String errorMessage) {
        ToolExecutionResult result = new ToolExecutionResult();
        result.setSkillId(skillId);
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
