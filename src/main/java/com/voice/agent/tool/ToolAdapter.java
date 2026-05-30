package com.voice.agent.tool;

import com.voice.agent.skill.SkillDefinition;

import java.util.Map;

public interface ToolAdapter {
    String getType();

    ToolExecutionResult execute(SkillDefinition skill, Map<String, Object> arguments);
}
