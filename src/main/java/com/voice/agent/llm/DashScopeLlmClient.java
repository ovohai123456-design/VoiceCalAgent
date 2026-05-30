package com.voice.agent.llm;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * DashScope OpenAI-compatible LLM client。
 *
 * <p>这里只返回模型文本，不在这一层解析业务 JSON，方便后续替换其他模型供应商。</p>
 */
@Component
public class DashScopeLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(DashScopeLlmClient.class);

    @Value("${llm.dashscope.api-key:}")
    private String apiKey;

    @Value("${llm.dashscope.model:qwen-flash}")
    private String model;

    @Value("${llm.dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        if (!StringUtils.hasText(apiKey) || apiKey.startsWith("${")) {
            log.warn("LLM request skipped provider=dashscope reason=api_key_missing");
            throw new IllegalStateException("DASHSCOPE_API_KEY 未配置");
        }

        long startedAt = System.currentTimeMillis();
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        log.info("LLM request start provider=dashscope model={} baseUrl={}", model, normalizedBaseUrl);
        try {
            OpenAIClient client = OpenAIOkHttpClient.builder()
                    .apiKey(apiKey)
                    .baseUrl(normalizedBaseUrl)
                    .build();

            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(model)
                    .addSystemMessage(systemPrompt)
                    .addUserMessage(userPrompt)
                    .temperature(0.0)
                    .maxTokens(1200)
                    .build();

            ChatCompletion completion = client.chat().completions().create(params);
            if (completion.choices().isEmpty()) {
                throw new IllegalStateException("LLM 没有返回候选结果");
            }
            String content = completion.choices().get(0).message().content()
                    .orElseThrow(() -> new IllegalStateException("LLM 返回内容为空"));
            log.info(
                    "LLM request success provider=dashscope model={} elapsedMs={} responseChars={}",
                    model,
                    System.currentTimeMillis() - startedAt,
                    content.length()
            );
            return content;
        } catch (RuntimeException e) {
            log.warn(
                    "LLM request failed provider=dashscope model={} elapsedMs={} error={}",
                    model,
                    System.currentTimeMillis() - startedAt,
                    e.toString()
            );
            throw e;
        }
    }

    private String normalizeBaseUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        String normalized = value.trim();
        if (normalized.endsWith("/chat/completions")) {
            return normalized.substring(0, normalized.length() - "/chat/completions".length());
        }
        return normalized;
    }
}
