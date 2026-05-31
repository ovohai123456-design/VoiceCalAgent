package com.voice.agent.agent;

import com.voice.agent.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * Handles casual conversation that does not require a calendar or tool action.
 */
@Component
public class ChatAgent {
    private static final Logger log = LoggerFactory.getLogger(ChatAgent.class);
    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "^(你好|您好|嗨|哈喽|hello|hi|hey|早上好|上午好|中午好|下午好|晚上好|在吗)[!！?？,.，。\\s]*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final String SYSTEM_PROMPT = String.join("\n",
            "你是 VoiceCal，一个友好、自然、简洁的中文日程助手。",
            "当前请求属于普通闲聊，请直接回答用户，不要输出 JSON。",
            "你可以进行日常对话，也可以介绍自己支持创建、查询、修改和删除日程。",
            "不要声称已经执行任何日程操作，也不要编造用户没有提供的信息。",
            "回复尽量简洁，通常不超过三句话。"
    );

    private final LlmClient llmClient;

    public ChatAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public String reply(String text, String history) {
        String userText = StringUtils.hasText(text) ? text.trim() : "";
        try {
            String response = llmClient.chat(buildSystemPrompt(history), userText);
            if (StringUtils.hasText(response)) {
                return response.trim();
            }
        } catch (RuntimeException e) {
            log.warn("ChatAgent LLM reply failed, fallback to local reply: {}", e.getMessage());
        }
        return fallbackReply(userText);
    }

    public static boolean isGreeting(String text) {
        return StringUtils.hasText(text) && GREETING_PATTERN.matcher(text.trim()).matches();
    }

    private String buildSystemPrompt(String history) {
        if (!StringUtils.hasText(history)) {
            return SYSTEM_PROMPT;
        }
        return SYSTEM_PROMPT + "\n\n最近对话：\n" + history;
    }

    private String fallbackReply(String text) {
        if (isGreeting(text)) {
            return "你好，我是 VoiceCal。你可以和我聊聊，也可以让我帮你创建、查询、修改或删除日程。";
        }
        if (text.contains("你是谁") || text.contains("你能做什么")) {
            return "我是 VoiceCal，你的日程助手。我可以陪你简单聊聊，也可以帮你管理日程。";
        }
        return "我在。你可以继续和我聊，也可以告诉我需要处理的日程。";
    }
}
