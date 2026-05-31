package com.voice.agent.mock;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MockNavigationProvider {
    public Map<String, Object> route(Map<String, Object> arguments) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("destination", arguments.get("destination"));
        result.put("status", "ROUTE_READY");
        result.put("provider", "mock");
        return result;
    }
}
