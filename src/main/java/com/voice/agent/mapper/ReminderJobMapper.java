package com.voice.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voice.agent.model.entity.ReminderJobEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ReminderJobMapper extends BaseMapper<ReminderJobEntity> {
    @Insert({
            "<script>",
            "INSERT INTO reminder_job (",
            "user_id, event_id, job_type, job_payload_json, run_at, status, retry_count, max_retry_count",
            ") VALUES ",
            "<foreach collection='jobs' item='job' separator=','>",
            "(",
            "#{job.userId}, #{job.eventId}, #{job.jobType}, #{job.jobPayloadJson}, #{job.runAt}, ",
            "#{job.status}, #{job.retryCount}, #{job.maxRetryCount}",
            ")",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("jobs") List<ReminderJobEntity> jobs);

    @Update("UPDATE reminder_job SET status = 'CANCELED', updated_at = NOW() "
            + "WHERE status = 'PENDING' AND event_id IN ("
            + "SELECT id FROM calendar_event WHERE recurrence_series_id = #{seriesId}"
            + ")")
    int cancelPendingForSeries(@Param("seriesId") String seriesId);

    @Delete("DELETE FROM reminder_job WHERE event_id IN ("
            + "SELECT id FROM calendar_event WHERE recurrence_series_id = #{seriesId}"
            + ")")
    int deleteForSeries(@Param("seriesId") String seriesId);
}
