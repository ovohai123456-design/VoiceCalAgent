package com.voice.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voice.agent.agent.CommandWorkflowService;
import com.voice.agent.agent.ExecutionLogWriter;
import com.voice.agent.mapper.CommandTaskMapper;
import com.voice.agent.mapper.ExecutionLogMapper;
import com.voice.agent.model.entity.ExecutionLogEntity;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CommandWorkflowServiceTest {
    @Test
    void shouldPersistCompletedLogThroughAsyncWriter() {
        CommandTaskMapper commandTaskMapper = mock(CommandTaskMapper.class);
        ExecutionLogMapper executionLogMapper = mock(ExecutionLogMapper.class);
        ExecutionLogWriter executionLogWriter = mock(ExecutionLogWriter.class);
        CommandWorkflowService service = new CommandWorkflowService(
                commandTaskMapper,
                executionLogMapper,
                executionLogWriter,
                new ObjectMapper()
        );

        ExecutionLogEntity log = service.addLog("task_001", 1, "router.route", "route", "agent", "request");
        service.markLogSuccess(log, "response");

        verify(executionLogWriter).write(log);
        verify(executionLogMapper, never()).insert(log);
        verify(executionLogMapper, never()).updateById(log);
    }
}
