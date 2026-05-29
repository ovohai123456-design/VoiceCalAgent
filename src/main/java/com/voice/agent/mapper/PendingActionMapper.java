package com.voice.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voice.agent.model.entity.PendingActionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * pending_action 表 Mapper，用于保存等待用户确认的高风险操作。
 */
@Mapper
public interface PendingActionMapper extends BaseMapper<PendingActionEntity> {
}
