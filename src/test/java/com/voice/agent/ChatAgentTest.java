package com.voice.agent;

import com.voice.agent.agent.ChatAgent;
import com.voice.agent.llm.LlmClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatAgentTest {
    @Test
    void shouldUseNaturalLlmReply() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.chat(anyString(), anyString())).thenReturn("  你好，有什么可以帮你？  ");

        assertEquals("你好，有什么可以帮你？", new ChatAgent(llmClient).reply("你好", null));
    }

    @Test
    void greetingShouldHaveLocalFallbackWhenLlmIsUnavailable() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.chat(anyString(), anyString())).thenThrow(new IllegalStateException("missing api key"));

        assertEquals(
                "你好，我是 VoiceCal。你可以和我聊聊，也可以让我帮你创建、查询、修改或删除日程。",
                new ChatAgent(llmClient).reply("你好", null)
        );
    }
}
