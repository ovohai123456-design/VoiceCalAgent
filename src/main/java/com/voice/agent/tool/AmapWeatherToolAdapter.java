package com.voice.agent.tool;

import com.voice.agent.skill.SkillDefinition;
import com.voice.agent.weather.AmapWeatherClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 高德实时天气 Skill 适配器。
 */
@Component
public class AmapWeatherToolAdapter implements ToolAdapter {
    private final AmapWeatherClient weatherClient;

    public AmapWeatherToolAdapter(AmapWeatherClient weatherClient) {
        this.weatherClient = weatherClient;
    }

    @Override
    public String getType() {
        return "amap-weather";
    }

    @Override
    public ToolExecutionResult execute(SkillDefinition skill, Map<String, Object> arguments) {
        if (!"weather.query".equals(skill.getSkillId())) {
            return ToolExecutionResult.failed(skill.getSkillId(), "高德天气适配器不支持该 Skill");
        }
        return ToolExecutionResult.success(
                skill.getSkillId(),
                weatherClient.queryLiveWeather(String.valueOf(arguments.get("location")))
        );
    }
}
