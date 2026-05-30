package com.voice.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voice.agent.model.entity.CommandTaskEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * command_task 表 Mapper，一条记录对应一次用户指令处理。
 */
@Mapper
public interface CommandTaskMapper extends BaseMapper<CommandTaskEntity> {
}
