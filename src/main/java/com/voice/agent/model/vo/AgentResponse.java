package com.voice.agent.model.vo;

import java.util.ArrayList;
import java.util.List;

public class AgentResponse {
    private Boolean success;
    private String taskId;
    private Boolean needConfirm;
    private String confirmToken;
    private Boolean needClarify;
    private List<String> missingFields = new ArrayList<>();
    private String replyText;
    private String speakText;
    private Object data;

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Boolean getNeedConfirm() {
        return needConfirm;
    }

    public void setNeedConfirm(Boolean needConfirm) {
        this.needConfirm = needConfirm;
    }

    public String getConfirmToken() {
        return confirmToken;
    }

    public void setConfirmToken(String confirmToken) {
        this.confirmToken = confirmToken;
    }

    public Boolean getNeedClarify() {
        return needClarify;
    }

    public void setNeedClarify(Boolean needClarify) {
        this.needClarify = needClarify;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }

    public void setMissingFields(List<String> missingFields) {
        this.missingFields = missingFields;
    }

    public String getReplyText() {
        return replyText;
    }

    public void setReplyText(String replyText) {
        this.replyText = replyText;
    }

    public String getSpeakText() {
        return speakText;
    }

    public void setSpeakText(String speakText) {
        this.speakText = speakText;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
