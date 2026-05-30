package com.voice.agent.controller;

import com.voice.agent.model.vo.ApiResponse;
import com.voice.agent.skill.SkillDefinition;
import com.voice.agent.skill.SkillRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/skills")
public class SkillController {
    private final SkillRegistry skillRegistry;

    @Value("${voicecal.skill.reload-enabled:true}")
    private Boolean reloadEnabled;

    public SkillController(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    @GetMapping
    public ApiResponse<List<SkillDefinition>> list() {
        return ApiResponse.ok(skillRegistry.list());
    }

    @GetMapping("/{skillId}")
    public ApiResponse<SkillDefinition> get(@PathVariable String skillId) {
        return ApiResponse.ok(skillRegistry.get(skillId));
    }

    @PostMapping("/reload")
    public ApiResponse<List<SkillDefinition>> reload() {
        if (!Boolean.TRUE.equals(reloadEnabled)) {
            return ApiResponse.fail("SKILL_RELOAD_DISABLED", "Skill reload is disabled");
        }
        return ApiResponse.ok(skillRegistry.reload());
    }
}
