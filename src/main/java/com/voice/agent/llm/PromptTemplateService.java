package com.voice.agent.llm;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Prompt 模板加载和变量替换服务。
 */
@Service
public class PromptTemplateService {
    private final ResourceLoader resourceLoader;

    public PromptTemplateService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String render(String classpathLocation, Map<String, String> variables) {
        String template = load(classpathLocation);
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered;
    }

    private String load(String classpathLocation) {
        Resource resource = resourceLoader.getResource("classpath:" + classpathLocation);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("加载 Prompt 模板失败：" + classpathLocation, e);
        }
    }
}
