package com.voice.agent.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ToolAdapterFactory {
    private final Map<String, ToolAdapterInvoker> adapters = new LinkedHashMap<>();

    public ToolAdapterFactory(
            NativeToolAdapter nativeToolAdapter,
            MockToolAdapter mockToolAdapter,
            SchedulerToolAdapter schedulerToolAdapter,
            AmapWeatherToolAdapter amapWeatherToolAdapter
    ) {
        adapters.put(nativeToolAdapter.getType(), nativeToolAdapter::execute);
        adapters.put(mockToolAdapter.getType(), mockToolAdapter::execute);
        adapters.put(schedulerToolAdapter.getType(), schedulerToolAdapter::execute);
        adapters.put(amapWeatherToolAdapter.getType(), amapWeatherToolAdapter::execute);
    }

    public ToolAdapterInvoker get(String type) {
        ToolAdapterInvoker adapter = adapters.get(type);
        if (adapter == null) {
            throw new IllegalArgumentException("Tool adapter is not registered: " + type);
        }
        return adapter;
    }

    @FunctionalInterface
    public interface ToolAdapterInvoker {
        ToolExecutionResult execute(com.voice.agent.skill.SkillDefinition skill, Map<String, Object> arguments);
    }
}
