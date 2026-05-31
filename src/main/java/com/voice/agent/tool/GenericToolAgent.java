package com.voice.agent.tool;

import com.voice.agent.agent.CommandWorkflowService;
import com.voice.agent.model.entity.ExecutionLogEntity;
import com.voice.agent.skill.SkillDefinition;
import com.voice.agent.skill.SkillRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GenericToolAgent {
    private static final Pattern REFERENCE_PATTERN = Pattern.compile("^\\$\\{([^.}]+)\\.([^}]+)}$");

    private final SkillRegistry skillRegistry;
    private final ToolAdapterFactory adapterFactory;
    private final CommandWorkflowService workflowService;

    public GenericToolAgent(
            SkillRegistry skillRegistry,
            ToolAdapterFactory adapterFactory,
            CommandWorkflowService workflowService
    ) {
        this.skillRegistry = skillRegistry;
        this.adapterFactory = adapterFactory;
        this.workflowService = workflowService;
    }

    public ToolExecutionSummary execute(String taskId, List<ToolActionStep> steps) {
        ToolExecutionSummary summary = new ToolExecutionSummary();
        Map<String, Object> context = new LinkedHashMap<>();
        if (steps == null) {
            return summary;
        }
        for (ToolActionStep step : steps) {
            SkillDefinition skill = skillRegistry.get(step.getSkillId());
            Map<String, Object> arguments = resolveArguments(step.getArguments(), context);
            validateArguments(skill, arguments);

            ExecutionLogEntity log = workflowService.addLog(
                    taskId,
                    step.getStepOrder(),
                    skill.getSkillId(),
                    skill.getName(),
                    skill.getExecutor().getType(),
                    arguments
            );
            try {
                ToolExecutionResult result = adapterFactory.get(skill.getExecutor().getType()).execute(skill, arguments);
                if (!Boolean.TRUE.equals(result.getSuccess())) {
                    throw new IllegalStateException(result.getErrorMessage());
                }
                validateOutput(skill, result.getData());
                workflowService.markLogSuccess(log, result.getData());
                summary.getResults().add(result);
                if (StringUtils.hasText(step.getOutputAlias())) {
                    context.put(step.getOutputAlias(), result.getData());
                }
            } catch (RuntimeException e) {
                workflowService.markLogFailed(log, e.getMessage());
                ToolExecutionResult failed = ToolExecutionResult.failed(step.getSkillId(), e.getMessage());
                summary.getResults().add(failed);
                if (!"CONTINUE".equalsIgnoreCase(step.getOnFailure())) {
                    throw e;
                }
            }
        }
        summary.setContext(context);
        return summary;
    }

    private Map<String, Object> resolveArguments(Map<String, Object> arguments, Map<String, Object> context) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        if (arguments == null) {
            return resolved;
        }
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            resolved.put(entry.getKey(), resolveValue(entry.getValue(), context));
        }
        return resolved;
    }

    private Object resolveValue(Object value, Map<String, Object> context) {
        if (!(value instanceof String)) {
            return value;
        }
        Matcher matcher = REFERENCE_PATTERN.matcher((String) value);
        if (!matcher.matches()) {
            return value;
        }
        Object output = context.get(matcher.group(1));
        if (!(output instanceof Map)) {
            return null;
        }
        return ((Map<?, ?>) output).get(matcher.group(2));
    }

    private void validateArguments(SkillDefinition skill, Map<String, Object> arguments) {
        Object required = skill.getInputSchema().get("required");
        if (!(required instanceof Collection)) {
            return;
        }
        for (Object field : (Collection<?>) required) {
            Object value = arguments.get(String.valueOf(field));
            if (value == null || (value instanceof String && !StringUtils.hasText((String) value))) {
                throw new IllegalArgumentException("Missing skill argument " + field + " for " + skill.getSkillId());
            }
        }
    }

    private void validateOutput(SkillDefinition skill, Object data) {
        Object required = skill.getOutputSchema().get("required");
        if (!(required instanceof Collection)) {
            return;
        }
        if (!(data instanceof Map)) {
            throw new IllegalStateException("Skill output must be an object: " + skill.getSkillId());
        }
        for (Object field : (Collection<?>) required) {
            Object value = ((Map<?, ?>) data).get(String.valueOf(field));
            if (value == null || (value instanceof String && !StringUtils.hasText((String) value))) {
                throw new IllegalStateException("Missing skill output " + field + " for " + skill.getSkillId());
            }
        }
    }

    public static class ToolExecutionSummary {
        private List<ToolExecutionResult> results = new ArrayList<>();
        private Map<String, Object> context = new LinkedHashMap<>();

        public List<ToolExecutionResult> getResults() {
            return results;
        }

        public void setResults(List<ToolExecutionResult> results) {
            this.results = results;
        }

        public Map<String, Object> getContext() {
            return context;
        }

        public void setContext(Map<String, Object> context) {
            this.context = context;
        }
    }
}
