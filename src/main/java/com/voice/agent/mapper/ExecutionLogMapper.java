package com.voice.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voice.agent.model.entity.ExecutionLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * execution_log 表 Mapper，只记录一次任务内部的路由、检查、确认、执行等过程。
 */
@Mapper
public interface ExecutionLogMapper extends BaseMapper<ExecutionLogEntity> {
}
