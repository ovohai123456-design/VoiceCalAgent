package com.voice.agent.llm;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 从模型输出中提取 JSON 对象。
 *
 * <p>模型偶尔会包一层 Markdown 代码块，这里按花括号配平提取第一个完整 JSON。</p>
 */
@Component
public class LlmJsonExtractor {
    public String extractObject(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            throw new IllegalArgumentException("LLM 原始输出为空");
        }

        String text = stripMarkdownFence(rawText.trim());
        int start = text.indexOf('{');
        if (start < 0) {
            throw new IllegalArgumentException("LLM 输出中没有 JSON 对象");
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        throw new IllegalArgumentException("LLM 输出 JSON 花括号不完整");
    }

    private String stripMarkdownFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        String stripped = text.replaceFirst("^```[a-zA-Z]*\\s*", "");
        return stripped.replaceFirst("\\s*```$", "").trim();
    }
}
