package com.voice.agent.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("user_preference")
public class UserPreferenceEntity {
    @TableId
    private Long userId;
    private Integer defaultDurationMinutes;
    private Integer defaultReminderMinutes;
    private String defaultLocation;
    private String defaultEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getDefaultDurationMinutes() { return defaultDurationMinutes; }
    public void setDefaultDurationMinutes(Integer value) { this.defaultDurationMinutes = value; }
    public Integer getDefaultReminderMinutes() { return defaultReminderMinutes; }
    public void setDefaultReminderMinutes(Integer value) { this.defaultReminderMinutes = value; }
    public String getDefaultLocation() { return defaultLocation; }
    public void setDefaultLocation(String defaultLocation) { this.defaultLocation = defaultLocation; }
    public String getDefaultEmail() { return defaultEmail; }
    public void setDefaultEmail(String defaultEmail) { this.defaultEmail = defaultEmail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
