package com.voice.agent;

import com.voice.agent.tool.ToolExecutionResult;
import com.voice.agent.tool.ToolResultReplyFormatter;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolResultReplyFormatterTest {
    @Test
    void weatherResultShouldBeReadableForUsers() {
        Map<String, Object> weather = new LinkedHashMap<>();
        weather.put("location", "上海");
        weather.put("condition", "晴");
        weather.put("temperature_celsius", 26);
        weather.put("provider", "mock");

        String reply = new ToolResultReplyFormatter().format(Collections.singletonList(
                ToolExecutionResult.success("weather.query", weather)
        ));

        assertEquals("上海当前天气：晴，26°C。", reply);
    }
}
