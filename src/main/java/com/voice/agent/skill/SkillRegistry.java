package com.voice.agent.skill;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SkillRegistry {
    private final SkillLoader skillLoader;
    private final SkillValidator skillValidator;
    private volatile Map<String, SkillDefinition> definitions = Collections.emptyMap();

    public SkillRegistry(SkillLoader skillLoader, SkillValidator skillValidator) {
        this.skillLoader = skillLoader;
        this.skillValidator = skillValidator;
    }

    @PostConstruct
    public synchronized List<SkillDefinition> reload() {
        Map<String, SkillDefinition> loaded = new LinkedHashMap<>();
        for (SkillDefinition definition : skillLoader.load()) {
            skillValidator.validate(definition);
            if (loaded.put(definition.getSkillId(), definition) != null) {
                throw new IllegalArgumentException("Duplicate skill_id: " + definition.getSkillId());
            }
        }
        definitions = Collections.unmodifiableMap(loaded);
        return list();
    }

    public List<SkillDefinition> list() {
        List<SkillDefinition> result = new ArrayList<>(definitions.values());
        result.sort(Comparator.comparing(SkillDefinition::getSkillId));
        return result;
    }

    public SkillDefinition get(String skillId) {
        SkillDefinition definition = definitions.get(skillId);
        if (definition == null) {
            throw new IllegalArgumentException("Skill not found: " + skillId);
        }
        return definition;
    }
}
