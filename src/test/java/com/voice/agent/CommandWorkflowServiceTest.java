package com.voice.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voice.agent.agent.CommandWorkflowService;
import com.voice.agent.agent.ExecutionLogWriter;
import com.voice.agent.mapper.CommandTaskMapper;
import com.voice.agent.mapper.ExecutionLogMapper;
import com.voice.agent.model.dto.AgentExecuteRequest;
import com.voice.agent.model.entity.CommandTaskEntity;
import com.voice.agent.model.entity.ExecutionLogEntity;
import com.voice.agent.stream.AgentEventStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                new ObjectMapper(),
                mock(AgentEventStreamService.class)
        );

        ExecutionLogEntity log = service.addLog("task_001", 1, "router.route", "route", "agent", "request");
        service.markLogSuccess(log, "response");

        verify(executionLogWriter).write(log);
        verify(executionLogMapper, never()).insert(log);
        verify(executionLogMapper, never()).updateById(log);
    }

    @Test
    void shouldCommitTaskIndependentlyAndRegisterStreamAfterCommit() throws Exception {
        CommandTaskMapper commandTaskMapper = mock(CommandTaskMapper.class);
        AgentEventStreamService eventStreamService = mock(AgentEventStreamService.class);
        CommandWorkflowService service = new CommandWorkflowService(
                commandTaskMapper,
                mock(ExecutionLogMapper.class),
                mock(ExecutionLogWriter.class),
                new ObjectMapper(),
                eventStreamService
        );
        AgentExecuteRequest request = new AgentExecuteRequest();
        request.setUserId(1L);
        request.setSessionId("session_001");
        request.setInputType("TEXT");
        request.setText("delete today's meeting");

        Method createTask = CommandWorkflowService.class.getMethod("createTask", AgentExecuteRequest.class);
        Transactional transactional = createTask.getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());

        TransactionSynchronizationManager.initSynchronization();
        try {
            CommandTaskEntity task = service.createTask(request);

            verify(commandTaskMapper).insert(task);
            verify(eventStreamService, never()).registerTask(task);

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            verify(eventStreamService).registerTask(task);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
