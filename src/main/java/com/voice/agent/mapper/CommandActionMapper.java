package com.voice.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voice.agent.model.entity.CommandActionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * command_action 表 Mapper，一条记录对应一次可执行的业务动作。
 */
@Mapper
public interface CommandActionMapper extends BaseMapper<CommandActionEntity> {
}
