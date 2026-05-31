package com.voice.agent;

import com.voice.agent.agent.CommandWorkflowService;
import com.voice.agent.model.entity.ExecutionLogEntity;
import com.voice.agent.skill.ExecutorDefinition;
import com.voice.agent.skill.SkillDefinition;
import com.voice.agent.skill.SkillRegistry;
import com.voice.agent.tool.GenericToolAgent;
import com.voice.agent.tool.ToolActionStep;
import com.voice.agent.tool.ToolAdapterFactory;
import com.voice.agent.tool.ToolExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenericToolAgentTest {
    @Test
    void executeShouldResolveSkillAndRecordToolResult() {
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        ToolAdapterFactory adapterFactory = mock(ToolAdapterFactory.class);
        CommandWorkflowService workflowService = mock(CommandWorkflowService.class);
        SkillDefinition skill = skill();
        Map<String, Object> resultData = Collections.singletonMap("meeting_url", "https://meeting.local/1");
        ToolAdapterFactory.ToolAdapterInvoker invoker =
                (definition, arguments) -> ToolExecutionResult.success(definition.getSkillId(), resultData);

        when(skillRegistry.get("meeting.create")).thenReturn(skill);
        when(adapterFactory.get("mock")).thenReturn(invoker);
        when(workflowService.addLog(any(), any(), any(), any(), any(), any())).thenReturn(new ExecutionLogEntity());

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("title", "project meeting");
        ToolActionStep step = ToolActionStep.of(10, "meeting.create", "meeting", arguments);
        GenericToolAgent agent = new GenericToolAgent(skillRegistry, adapterFactory, workflowService);

        GenericToolAgent.ToolExecutionSummary summary = agent.execute("task_001", Collections.singletonList(step));

        assertEquals(resultData, summary.getContext().get("meeting"));
        verify(workflowService).markLogSuccess(any(ExecutionLogEntity.class), any());
    }

    @Test
    void executeShouldResolveOutputReferenceForFollowingSkill() {
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        ToolAdapterFactory adapterFactory = mock(ToolAdapterFactory.class);
        CommandWorkflowService workflowService = mock(CommandWorkflowService.class);
        SkillDefinition meetingSkill = skill();
        SkillDefinition smsSkill = skill("sms.send", "receiver", "content");
        AtomicReference<Map<String, Object>> smsArguments = new AtomicReference<>();
        ToolAdapterFactory.ToolAdapterInvoker invoker = (definition, arguments) -> {
            if ("meeting.create".equals(definition.getSkillId())) {
                return ToolExecutionResult.success(
                        definition.getSkillId(),
                        Collections.singletonMap("code", "123456789")
                );
            }
            smsArguments.set(arguments);
            return ToolExecutionResult.success(
                    definition.getSkillId(),
                    Collections.singletonMap("sent", true)
            );
        };

        when(skillRegistry.get("meeting.create")).thenReturn(meetingSkill);
        when(skillRegistry.get("sms.send")).thenReturn(smsSkill);
        when(adapterFactory.get("mock")).thenReturn(invoker);
        when(workflowService.addLog(any(), any(), any(), any(), any(), any())).thenReturn(new ExecutionLogEntity());

        Map<String, Object> meetingArguments = new LinkedHashMap<>();
        meetingArguments.put("title", "project meeting");
        Map<String, Object> smsInput = new LinkedHashMap<>();
        smsInput.put("receiver", "${meeting.code}");
        smsInput.put("content", "会议号已创建");
        GenericToolAgent agent = new GenericToolAgent(skillRegistry, adapterFactory, workflowService);

        agent.execute(
                "task_001",
                Arrays.asList(
                        ToolActionStep.of(10, "meeting.create", "meeting", meetingArguments),
                        ToolActionStep.of(20, "sms.send", "sms", smsInput)
                )
        );

        assertEquals("123456789", smsArguments.get().get("receiver"));
    }

    private SkillDefinition skill() {
        return skill("meeting.create", "title");
    }

    private SkillDefinition skill(String skillId, String... requiredFields) {
        ExecutorDefinition executor = new ExecutorDefinition();
        executor.setType("mock");
        executor.setMockName(skillId);
        SkillDefinition skill = new SkillDefinition();
        skill.setSkillId(skillId);
        skill.setName(skillId);
        skill.setDescription(skillId);
        skill.setExecutor(executor);
        skill.setInputSchema(Collections.singletonMap("required", Arrays.asList(requiredFields)));
        return skill;
    }
}
