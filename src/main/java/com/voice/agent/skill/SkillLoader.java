package com.voice.agent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Skill 描述文件加载器。
 */
@Component
public class SkillLoader {
    private final ObjectMapper objectMapper;
    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    @Value("${voicecal.skill.location:classpath:/skills/**/*.yaml}")
    private String location;

    public SkillLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    public List<SkillDefinition> load() {
        try {
            Resource[] resources = resourceResolver.getResources(location);
            List<SkillDefinition> definitions = new ArrayList<>();
            for (Resource resource : resources) {
                definitions.add(read(resource));
            }
            return definitions;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load skill manifests from " + location, e);
        }
    }

    private SkillDefinition read(Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            Map<String, Object> yaml = new Yaml().load(inputStream);
            return objectMapper.convertValue(yaml, SkillDefinition.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read skill manifest: " + resource.getFilename(), e);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid skill manifest: " + resource.getFilename(), e);
        }
    }
}
