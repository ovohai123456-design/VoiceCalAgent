package com.voice.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voice.agent.model.entity.AgentTaskEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * agent_task 表 Mapper，一条记录对应一次用户指令处理。
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTaskEntity> {
}
