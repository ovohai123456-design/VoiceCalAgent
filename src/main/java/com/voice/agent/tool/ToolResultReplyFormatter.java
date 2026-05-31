package com.voice.agent.tool;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 将工具执行结果转换为适合直接展示给用户的自然语言。
 *
 * <p>工具原始数据仍然通过 AgentResponse.data 返回，这里只负责生成对话回复。</p>
 */
@Component
public class ToolResultReplyFormatter {
    public String format(List<ToolExecutionResult> results) {
        if (results == null || results.isEmpty()) {
            return "任务已完成。";
        }

        StringBuilder reply = new StringBuilder();
        for (ToolExecutionResult result : results) {
            if (reply.length() > 0) {
                reply.append("\n");
            }
            reply.append(format(result));
        }
        return reply.toString();
    }

    private String format(ToolExecutionResult result) {
        if (!Boolean.TRUE.equals(result.getSuccess())) {
            return StringUtils.hasText(result.getErrorMessage())
                    ? "执行失败：" + result.getErrorMessage()
                    : "执行失败，请稍后重试。";
        }

        Map<?, ?> data = result.getData() instanceof Map ? (Map<?, ?>) result.getData() : null;
        if ("weather.query".equals(result.getSkillId())) {
            return formatWeather(data);
        }
        if ("navigation.route".equals(result.getSkillId())) {
            return formatNavigation(data);
        }
        if ("sms.send".equals(result.getSkillId())) {
            return formatSms(data);
        }
        if ("email.schedule".equals(result.getSkillId())) {
            return "邮件提醒已安排。";
        }
        if ("meeting.create".equals(result.getSkillId())) {
            return formatMeeting(data);
        }
        return "已完成「" + result.getSkillId() + "」。";
    }

    private String formatWeather(Map<?, ?> data) {
        String location = value(data, "location");
        String condition = value(data, "condition");
        String temperature = value(data, "temperature_celsius");
        return location + "当前天气：" + condition + "，" + temperature + "°C。";
    }

    private String formatNavigation(Map<?, ?> data) {
        return "已为你生成前往「" + value(data, "destination") + "」的导航路线。";
    }

    private String formatSms(Map<?, ?> data) {
        return "短信提醒已发送给「" + value(data, "receiver") + "」。";
    }

    private String formatMeeting(Map<?, ?> data) {
        String code = value(data, "meeting_code", "code");
        String url = value(data, "meeting_url", "url");
        return "已创建腾讯会议，会议号 " + code + "，入会链接 " + url + "。";
    }

    private String value(Map<?, ?> data, String... keys) {
        if (data == null) {
            return "未知";
        }
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return "未知";
    }
}
