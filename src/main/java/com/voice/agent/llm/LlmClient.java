package com.voice.agent.llm;

public interface LlmClient {
    String chat(String systemPrompt, String userPrompt);
}
