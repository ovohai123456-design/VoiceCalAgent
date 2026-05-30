package com.voice.agent.agent;

import com.voice.agent.mapper.ExecutionLogMapper;
import com.voice.agent.model.entity.ExecutionLogEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Persists diagnostic logs outside the user-facing request path.
 */
@Service
@Slf4j
public class ExecutionLogWriter {
    private final ExecutionLogMapper executionLogMapper;

    public ExecutionLogWriter(ExecutionLogMapper executionLogMapper) {
        this.executionLogMapper = executionLogMapper;
    }

    @Async
    public void write(ExecutionLogEntity logEntity) {
        try {
            executionLogMapper.insert(logEntity);
        } catch (RuntimeException e) {
            log.warn(
                    "Execution log persist failed taskId={} stepOrder={} error={}",
                    logEntity.getTaskId(),
                    logEntity.getStepOrder(),
                    e.toString()
            );
        }
    }
}
