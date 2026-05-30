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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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

    private SkillDefinition skill() {
        ExecutorDefinition executor = new ExecutorDefinition();
        executor.setType("mock");
        executor.setMockName("meeting.create");
        SkillDefinition skill = new SkillDefinition();
        skill.setSkillId("meeting.create");
        skill.setName("Create meeting");
        skill.setDescription("Create meeting link");
        skill.setExecutor(executor);
        skill.setInputSchema(Collections.singletonMap("required", Collections.singletonList("title")));
        return skill;
    }
}
