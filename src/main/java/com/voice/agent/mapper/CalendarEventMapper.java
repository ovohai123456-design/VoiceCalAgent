package com.voice.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voice.agent.model.entity.CalendarEventEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日程表 calendar_event 的 MyBatis-Plus Mapper。
 *
 */
@Mapper
public interface CalendarEventMapper extends BaseMapper<CalendarEventEntity> {
}
