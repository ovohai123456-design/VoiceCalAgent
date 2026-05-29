package com.voice.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voice.agent.model.entity.AgentStepEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * agent_step 表 Mapper，用于记录一次任务内部的路由、检查、确认、执行等步骤。
 */
@Mapper
public interface AgentStepMapper extends BaseMapper<AgentStepEntity> {
}
