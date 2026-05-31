package com.voice.agent.mock;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MockWeatherProvider {
    public Map<String, Object> query(Map<String, Object> arguments) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("location", arguments.get("location"));
        result.put("condition", "晴");
        result.put("temperature_celsius", 26);
        result.put("provider", "mock");
        return result;
    }
}
