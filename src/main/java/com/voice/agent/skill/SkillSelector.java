package com.voice.agent.skill;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class SkillSelector {
    private final SkillRegistry skillRegistry;

    public SkillSelector(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    public List<SkillDefinition> select(String text) {
        List<SkillDefinition> matches = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return matches;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (SkillDefinition skill : skillRegistry.list()) {
            if (!Boolean.TRUE.equals(skill.getEnabled())) {
                continue;
            }
            if (matches(skill, normalized)) {
                matches.add(skill);
            }
        }
        return matches;
    }

    private boolean matches(SkillDefinition skill, String normalized) {
        for (String example : skill.getTriggerExamples()) {
            if (StringUtils.hasText(example) && hasSharedKeyword(normalized, example.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return StringUtils.hasText(skill.getDescription())
                && hasSharedKeyword(normalized, skill.getDescription().toLowerCase(Locale.ROOT));
    }

    private boolean hasSharedKeyword(String text, String reference) {
        for (String token : reference.split("[\\s,，。；、]+")) {
            if (token.length() >= 2 && text.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
