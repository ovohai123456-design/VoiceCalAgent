package com.voice.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voice.agent.skill.NativeToolRegistry;
import com.voice.agent.skill.SkillDefinition;
import com.voice.agent.skill.SkillLoader;
import com.voice.agent.skill.SkillRegistry;
import com.voice.agent.skill.SkillValidator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SkillRegistryTest {
    @Test
    void reloadShouldLoadMockSkillManifests() {
        SkillLoader loader = new SkillLoader(new ObjectMapper());
        ReflectionTestUtils.setField(loader, "location", "classpath:/skills/*.yaml");
        NativeToolRegistry nativeTools = new NativeToolRegistry();
        nativeTools.register("calendar.create", arguments -> null);
        nativeTools.register("calendar.query", arguments -> null);
        nativeTools.register("calendar.update", arguments -> null);
        nativeTools.register("calendar.delete", arguments -> null);
        nativeTools.register("calendar.conflict_check", arguments -> null);
        nativeTools.register("calendar.resolve_event", arguments -> null);
        nativeTools.register("contact.query", arguments -> null);
        SkillRegistry registry = new SkillRegistry(loader, new SkillValidator(nativeTools));

        List<SkillDefinition> skills = registry.reload();

        assertEquals(10, skills.size());
        assertEquals("meeting.create", registry.get("meeting.create").getSkillId());
        assertEquals("mock", registry.get("sms.send").getExecutor().getType());
        assertNotNull(registry.get("sms.send").getInputSchema().get("required"));
    }
}
