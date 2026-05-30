package com.voice.agent.mock;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class MockEmailProvider {
    public Map<String, Object> send(Map<String, Object> arguments) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message_id", "email_" + UUID.randomUUID().toString().replace("-", ""));
        result.put("receiver", arguments.get("receiver"));
        result.put("status", "SENT");
        result.put("provider", "mock");
        return result;
    }
}
