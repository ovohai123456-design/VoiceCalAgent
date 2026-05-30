package com.voice.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voice.agent.model.entity.CalendarEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 日程表 calendar_event 的 MyBatis-Plus Mapper。
 *
 */
@Mapper
public interface CalendarEventMapper extends BaseMapper<CalendarEventEntity> {
    @Insert({
            "<script>",
            "INSERT INTO calendar_event (",
            "user_id, title, start_time, end_time, location, description, meeting_url, reminder_minutes, ",
            "source, status, idempotency_key, source_task_id, recurrence_series_id, recurrence_index, created_at, updated_at",
            ") VALUES ",
            "<foreach collection='events' item='event' separator=','>",
            "(",
            "#{event.userId}, #{event.title}, #{event.startTime}, #{event.endTime}, #{event.location}, ",
            "#{event.description}, #{event.meetingUrl}, #{event.reminderMinutes}, #{event.source}, #{event.status}, ",
            "#{event.idempotencyKey}, #{event.sourceTaskId}, #{event.recurrenceSeriesId}, #{event.recurrenceIndex}, ",
            "#{event.createdAt}, #{event.updatedAt}",
            ")",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("events") List<CalendarEventEntity> events);

    @Update("UPDATE calendar_event SET status = 'DELETED', updated_at = NOW() "
            + "WHERE user_id = #{userId} AND recurrence_series_id = #{seriesId} AND status <> 'DELETED'")
    int softDeleteSeries(@Param("seriesId") String seriesId, @Param("userId") Long userId);
}
