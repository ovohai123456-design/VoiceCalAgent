package com.voice.agent.mock;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class MockMeetingProvider {
    public Map<String, Object> create(Map<String, Object> arguments) {
        String meetingCode = String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000L, 1_000_000_000L));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("meeting_url", "https://meeting.voicecal.local/tencent/" + meetingCode);
        result.put("meeting_code", meetingCode);
        result.put("url", "https://meeting.voicecal.local/tencent/" + meetingCode);
        result.put("code", meetingCode);
        result.put("title", arguments.get("title"));
        result.put("provider", "TENCENT_MEETING");
        return result;
    }
}
