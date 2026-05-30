package com.voice.agent.tool;

import com.voice.agent.skill.NativeToolRegistry;
import com.voice.agent.skill.SkillDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NativeToolAdapter implements ToolAdapter {
    private final NativeToolRegistry nativeToolRegistry;

    public NativeToolAdapter(NativeToolRegistry nativeToolRegistry) {
        this.nativeToolRegistry = nativeToolRegistry;
    }

    public String getType() {
        return "native";
    }

    public ToolExecutionResult execute(SkillDefinition skill, Map<String, Object> arguments) {
        Object data = nativeToolRegistry.execute(skill.getExecutor().getToolKey(), arguments);
        return ToolExecutionResult.success(skill.getSkillId(), data);
    }
}
