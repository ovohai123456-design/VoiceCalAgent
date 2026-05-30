package com.voice.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voice.agent.model.entity.PendingConfirmationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * pending_confirmation 表 Mapper，只保存等待用户确认的记录。
 */
@Mapper
public interface PendingConfirmationMapper extends BaseMapper<PendingConfirmationEntity> {
}
