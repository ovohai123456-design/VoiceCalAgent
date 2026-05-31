package com.voice.agent.service;

import com.voice.agent.model.dto.ConflictCheckRequest;
import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.dto.EventResolveRequest;
import com.voice.agent.model.dto.FreeSlotRequest;
import com.voice.agent.model.dto.QueryEventRequest;
import com.voice.agent.model.dto.UpdateEventRequest;
import com.voice.agent.model.vo.CalendarEventVO;
import com.voice.agent.model.vo.ConflictResultVO;
import com.voice.agent.model.vo.FreeSlotVO;

import java.util.List;

/**
 * 日历核心业务接口。
 *
 * <p>这里先承载比赛版 P0 的真实日历能力：CRUD、冲突检测、空闲时间推荐和事件定位。
 * Agent、确认流、语音解析后续都应该通过这个接口操作日程，而不是直接访问 Mapper。</p>
 */
public interface CalendarService {
    /**
     * 创建日程。实现层会做参数校验、幂等检查和时间冲突检测。
     */
    CalendarEventVO createEvent(CreateEventRequest request);

    /**
     * Creates an event after CalendarAgent already validated conflicts in the same workflow.
     */
    CalendarEventVO createPreparedEvent(CreateEventRequest request);

    /**
     * 查询日程。时间范围查询使用重叠条件：event.start < rangeEnd && event.end > rangeStart。
     */
    List<CalendarEventVO> queryEvents(QueryEventRequest request);

    /**
     * 修改日程。变更时间时会排除当前事件后重新做冲突检测。
     */
    CalendarEventVO updateEvent(Long eventId, UpdateEventRequest request);

    /**
     * Updates an event after CalendarAgent already resolved the event and validated conflicts.
     */
    CalendarEventVO updatePreparedEvent(Long eventId, UpdateEventRequest request);

    /**
     * 删除日程。当前业务采用软删除，只把 status 改为 DELETED。
     */
    Boolean deleteEvent(Long eventId, Long userId);

    /**
     * Deletes one occurrence or every occurrence in the same recurring series.
     */
    Boolean deleteEvent(Long eventId, Long userId, String scope);

    /**
     * Deletes every event selected by a confirmed time-range operation.
     */
    int deleteEvents(List<Long> eventIds, Long userId);

    /**
     * 检测目标时间段是否和已有日程冲突，并在冲突时返回候选空闲时间。
     */
    ConflictResultVO checkConflict(ConflictCheckRequest request);

    /**
     * 在指定日期的工作时间内推荐空闲时间段。
     */
    List<FreeSlotVO> findFreeSlots(FreeSlotRequest request);

    /**
     * 根据标题关键词和时间范围定位候选事件，用于后续修改/删除确认流程。
     */
    List<CalendarEventVO> findCandidateEvents(EventResolveRequest request);
}
