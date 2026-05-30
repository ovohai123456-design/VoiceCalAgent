package com.voice.agent.tool;

import com.voice.agent.mock.MockEmailProvider;
import com.voice.agent.mock.MockMeetingProvider;
import com.voice.agent.mock.MockSmsProvider;
import com.voice.agent.skill.SkillDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MockToolAdapter implements ToolAdapter {
    private final MockMeetingProvider meetingProvider;
    private final MockSmsProvider smsProvider;
    private final MockEmailProvider emailProvider;

    public MockToolAdapter(
            MockMeetingProvider meetingProvider,
            MockSmsProvider smsProvider,
            MockEmailProvider emailProvider
    ) {
        this.meetingProvider = meetingProvider;
        this.smsProvider = smsProvider;
        this.emailProvider = emailProvider;
    }

    public String getType() {
        return "mock";
    }

    public ToolExecutionResult execute(SkillDefinition skill, Map<String, Object> arguments) {
        String mockName = skill.getExecutor().getMockName();
        if ("meeting.create".equals(mockName)) {
            return ToolExecutionResult.success(skill.getSkillId(), meetingProvider.create(arguments));
        }
        if ("sms.send".equals(mockName)) {
            return ToolExecutionResult.success(skill.getSkillId(), smsProvider.send(arguments));
        }
        if ("email.send".equals(mockName)) {
            return ToolExecutionResult.success(skill.getSkillId(), emailProvider.send(arguments));
        }
        return ToolExecutionResult.failed(skill.getSkillId(), "Unsupported mock provider: " + mockName);
    }
}
