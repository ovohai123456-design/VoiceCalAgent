package com.voice.agent.skill;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class SkillValidator {
    private static final Set<String> EXECUTOR_TYPES = new HashSet<>(Arrays.asList(
            "native", "mock", "scheduler", "amap-weather"
    ));
    private static final Set<String> MOCK_NAMES = new HashSet<>(Arrays.asList(
            "meeting.create", "sms.send", "email.send", "weather.query", "navigation.route"
    ));

    private final NativeToolRegistry nativeToolRegistry;

    public SkillValidator(NativeToolRegistry nativeToolRegistry) {
        this.nativeToolRegistry = nativeToolRegistry;
    }

    public void validate(SkillDefinition definition) {
        if (definition == null || !StringUtils.hasText(definition.getSkillId())) {
            throw new IllegalArgumentException("skill_id must not be empty");
        }
        if (!StringUtils.hasText(definition.getName()) || !StringUtils.hasText(definition.getDescription())) {
            throw new IllegalArgumentException("Skill name and description must not be empty: " + definition.getSkillId());
        }
        validateSchema(definition.getInputSchema(), "input_schema", definition.getSkillId());
        validateSchema(definition.getOutputSchema(), "output_schema", definition.getSkillId());
        ExecutorDefinition executor = definition.getExecutor();
        if (executor == null || !EXECUTOR_TYPES.contains(executor.getType())) {
            throw new IllegalArgumentException("Unsupported executor type for skill: " + definition.getSkillId());
        }
        if ("native".equals(executor.getType()) && !nativeToolRegistry.contains(executor.getToolKey())) {
            throw new IllegalArgumentException("Native tool is not registered: " + executor.getToolKey());
        }
        if ("mock".equals(executor.getType()) && !MOCK_NAMES.contains(executor.getMockName())) {
            throw new IllegalArgumentException("Mock provider is not registered: " + executor.getMockName());
        }
        if ("scheduler".equals(executor.getType()) && !StringUtils.hasText(executor.getJobType())) {
            throw new IllegalArgumentException("Scheduler job_type must not be empty: " + definition.getSkillId());
        }
    }

    private void validateSchema(java.util.Map<String, Object> schema, String name, String skillId) {
        if (schema == null || !"object".equals(schema.get("type"))) {
            throw new IllegalArgumentException(name + " must define type=object: " + skillId);
        }
    }
}
