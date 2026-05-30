package com.voice.agent.mock;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class MockMeetingProvider {
    public Map<String, Object> create(Map<String, Object> arguments) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("meeting_url", "https://meeting.voicecal.local/" + UUID.randomUUID().toString().replace("-", ""));
        result.put("title", arguments.get("title"));
        result.put("provider", "mock");
        return result;
    }
}
