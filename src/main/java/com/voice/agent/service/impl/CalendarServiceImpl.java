package com.voice.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.voice.agent.mapper.CalendarEventMapper;
import com.voice.agent.mapper.RecurrenceSeriesMapper;
import com.voice.agent.model.dto.ConflictCheckRequest;
import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.dto.EventResolveRequest;
import com.voice.agent.model.dto.FreeSlotRequest;
import com.voice.agent.model.dto.QueryEventRequest;
import com.voice.agent.model.dto.UpdateEventRequest;
import com.voice.agent.model.entity.CalendarEventEntity;
import com.voice.agent.model.entity.RecurrenceSeriesEntity;
import com.voice.agent.model.vo.CalendarEventVO;
import com.voice.agent.model.vo.ConflictResultVO;
import com.voice.agent.model.vo.FreeSlotVO;
import com.voice.agent.service.CalendarEmailService;
import com.voice.agent.service.CalendarService;
import com.voice.agent.service.ReminderJobService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 日历核心业务实现。
 *
 * <p>当前实现直接基于 MyBatis-Plus 操作 calendar_event 表，先保证日历能力真实可用。
 * 之后 Agent 确认、Workflow 记录和提醒任务可以在这个服务外层编排。</p>
 */
@Service
public class CalendarServiceImpl implements CalendarService {
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DELETED = "DELETED";
    private static final String SOURCE_API = "API";
    private static final String RECURRENCE_NONE = "NONE";
    private static final String RECURRENCE_DAILY = "DAILY";
    private static final String RECURRENCE_WEEKLY = "WEEKLY";
    private static final String RECURRENCE_MONTHLY = "MONTHLY";

    private final CalendarEventMapper calendarEventMapper;
    private final RecurrenceSeriesMapper recurrenceSeriesMapper;
    private final ReminderJobService reminderJobService;
    private final CalendarEmailService calendarEmailService;

    @Value("${voicecal.default-user-id:1}")
    private Long defaultUserId;

    @Value("${voicecal.calendar.default-duration-minutes:60}")
    private Integer defaultDurationMinutes;

    @Value("${voicecal.calendar.work-start-time:09:00}")
    private String workStartTime;

    @Value("${voicecal.calendar.work-end-time:22:00}")
    private String workEndTime;

    @Value("${voicecal.calendar.max-free-slot-count:3}")
    private Integer maxFreeSlotCount;

    @Value("${voicecal.calendar.recurrence-default-count:12}")
    private Integer recurrenceDefaultCount;

    @Value("${voicecal.calendar.recurrence-max-count:100}")
    private Integer recurrenceMaxCount;

    public CalendarServiceImpl(
            CalendarEventMapper calendarEventMapper,
            RecurrenceSeriesMapper recurrenceSeriesMapper,
            ReminderJobService reminderJobService,
            CalendarEmailService calendarEmailService
    ) {
        this.calendarEventMapper = calendarEventMapper;
        this.recurrenceSeriesMapper = recurrenceSeriesMapper;
        this.reminderJobService = reminderJobService;
        this.calendarEmailService = calendarEmailService;
    }

    @Override
    @Transactional
    public CalendarEventVO createEvent(CreateEventRequest request) {
        return createEventInternal(request, false);
    }

    @Override
    @Transactional
    public CalendarEventVO createPreparedEvent(CreateEventRequest request) {
        return createEventInternal(request, true);
    }

    private CalendarEventVO createEventInternal(CreateEventRequest request, boolean conflictAlreadyChecked) {
        validateCreateRequest(request);
        Long userId = resolveUserId(request.getUserId());

        // 客户端或 Agent 重试同一次创建请求时，优先返回已有结果，避免重复写入日程。
        CalendarEventEntity existing = findByIdempotencyKey(userId, request.getIdempotencyKey());
        if (existing != null) {
            return toVO(existing);
        }

        if (isRecurring(request)) {
            return createRecurringEvents(request, userId);
        }

        // 创建前必须先做冲突检测，避免同一用户同一时间段出现重叠日程。
        if (!conflictAlreadyChecked) {
            ConflictCheckRequest conflictRequest = new ConflictCheckRequest();
            conflictRequest.setUserId(userId);
            conflictRequest.setStartTime(request.getStartTime());
            conflictRequest.setEndTime(request.getEndTime());
            ConflictResultVO conflict = checkConflict(conflictRequest);
            if (Boolean.TRUE.equals(conflict.getHasConflict())) {
                throw new IllegalStateException("目标时间段已有日程冲突");
            }
        }

        CalendarEventEntity entity = new CalendarEventEntity();
        entity.setUserId(userId);
        entity.setTitle(request.getTitle().trim());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setLocation(trimToNull(request.getLocation()));
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setMeetingUrl(trimToNull(request.getMeetingUrl()));
        entity.setMeetingProvider(trimToNull(request.getMeetingProvider()));
        entity.setMeetingCode(trimToNull(request.getMeetingCode()));
        entity.setReminderMinutes(request.getReminderMinutes());
        entity.setSource(StringUtils.hasText(request.getSource()) ? request.getSource().trim() : SOURCE_API);
        entity.setStatus(STATUS_ACTIVE);
        entity.setIdempotencyKey(trimToNull(request.getIdempotencyKey()));
        entity.setSourceTaskId(trimToNull(request.getSourceTaskId()));

        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        calendarEventMapper.insert(entity);
        reminderJobService.createForEvent(entity, request.getEmailReceiver(), request.getEmailContent());
        calendarEmailService.notifyEventsCreatedAfterCommit(
                Collections.singletonList(entity),
                request.getEmailReceiver()
        );
        return toVO(entity);
    }

    @Override
    public List<CalendarEventVO> queryEvents(QueryEventRequest request) {
        QueryEventRequest safeRequest = request == null ? new QueryEventRequest() : request;
        Long userId = resolveUserId(safeRequest.getUserId());

        LambdaQueryWrapper<CalendarEventEntity> wrapper = Wrappers.lambdaQuery(CalendarEventEntity.class)
                .eq(CalendarEventEntity::getUserId, userId);

        if (StringUtils.hasText(safeRequest.getStatus())) {
            wrapper.eq(CalendarEventEntity::getStatus, safeRequest.getStatus().trim());
        } else {
            wrapper.ne(CalendarEventEntity::getStatus, STATUS_DELETED);
        }

        if (safeRequest.getStartTime() != null && safeRequest.getEndTime() != null) {
            validateTimeRange(safeRequest.getStartTime(), safeRequest.getEndTime());
            wrapper.lt(CalendarEventEntity::getStartTime, safeRequest.getEndTime())
                    .gt(CalendarEventEntity::getEndTime, safeRequest.getStartTime());
        } else if (safeRequest.getStartTime() != null) {
            wrapper.ge(CalendarEventEntity::getStartTime, safeRequest.getStartTime());
        } else if (safeRequest.getEndTime() != null) {
            wrapper.le(CalendarEventEntity::getEndTime, safeRequest.getEndTime());
        }

        if (StringUtils.hasText(safeRequest.getKeyword())) {
            wrapper.like(CalendarEventEntity::getTitle, safeRequest.getKeyword().trim());
        }

        wrapper.orderByAsc(CalendarEventEntity::getStartTime);
        return calendarEventMapper.selectList(wrapper)
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CalendarEventVO updateEvent(Long eventId, UpdateEventRequest request) {
        return updateEventInternal(eventId, request, false);
    }

    @Override
    @Transactional
    public CalendarEventVO updatePreparedEvent(Long eventId, UpdateEventRequest request) {
        return updateEventInternal(eventId, request, true);
    }

    private CalendarEventVO updateEventInternal(Long eventId, UpdateEventRequest request, boolean conflictAlreadyChecked) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId 不能为空");
        }
        if (request == null) {
            throw new IllegalArgumentException("更新参数不能为空");
        }

        Long userId = resolveUserId(request.getUserId());
        CalendarEventEntity entity = getActiveEvent(eventId, userId);

        // 允许局部更新：未传的时间字段沿用原值，但最终仍必须构成合法时间段。
        LocalDateTime newStart = request.getStartTime() == null ? entity.getStartTime() : request.getStartTime();
        LocalDateTime newEnd = request.getEndTime() == null ? entity.getEndTime() : request.getEndTime();
        validateTimeRange(newStart, newEnd);

        // 只有时间发生变化时才重新检测冲突，并排除当前被修改的事件。
        boolean timeChanged = !Objects.equals(entity.getStartTime(), newStart)
                || !Objects.equals(entity.getEndTime(), newEnd);
        if (timeChanged && !conflictAlreadyChecked) {
            ConflictCheckRequest conflictRequest = new ConflictCheckRequest();
            conflictRequest.setUserId(userId);
            conflictRequest.setStartTime(newStart);
            conflictRequest.setEndTime(newEnd);
            conflictRequest.setExcludeEventId(eventId);
            ConflictResultVO conflict = checkConflict(conflictRequest);
            if (Boolean.TRUE.equals(conflict.getHasConflict())) {
                throw new IllegalStateException("目标时间段已有日程冲突");
            }
        }

        if (StringUtils.hasText(request.getTitle())) {
            entity.setTitle(request.getTitle().trim());
        }
        entity.setStartTime(newStart);
        entity.setEndTime(newEnd);
        if (request.getLocation() != null) {
            entity.setLocation(trimToNull(request.getLocation()));
        }
        if (request.getDescription() != null) {
            entity.setDescription(trimToNull(request.getDescription()));
        }
        if (request.getMeetingUrl() != null) {
            entity.setMeetingUrl(trimToNull(request.getMeetingUrl()));
        }
        if (request.getMeetingProvider() != null) {
            entity.setMeetingProvider(trimToNull(request.getMeetingProvider()));
        }
        if (request.getMeetingCode() != null) {
            entity.setMeetingCode(trimToNull(request.getMeetingCode()));
        }
        if (request.getReminderMinutes() != null) {
            entity.setReminderMinutes(request.getReminderMinutes());
        }
        if (StringUtils.hasText(request.getSourceTaskId())) {
            entity.setSourceTaskId(request.getSourceTaskId().trim());
        }
        entity.setUpdatedAt(LocalDateTime.now());

        calendarEventMapper.updateById(entity);
        reminderJobService.syncForEvent(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public Boolean deleteEvent(Long eventId, Long userId) {
        return deleteEvent(eventId, userId, "SINGLE");
    }

    @Override
    @Transactional
    public Boolean deleteEvent(Long eventId, Long userId, String scope) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId 不能为空");
        }
        CalendarEventEntity entity = getActiveEvent(eventId, resolveUserId(userId));
        if ("SERIES".equalsIgnoreCase(scope) && StringUtils.hasText(entity.getRecurrenceSeriesId())) {
            int deletedCount = calendarEventMapper.softDeleteSeries(entity.getRecurrenceSeriesId(), entity.getUserId());
            RecurrenceSeriesEntity series = recurrenceSeriesMapper.selectById(entity.getRecurrenceSeriesId());
            if (series != null) {
                series.setStatus(STATUS_DELETED);
                series.setUpdatedAt(LocalDateTime.now());
                recurrenceSeriesMapper.updateById(series);
            }
            reminderJobService.deleteForSeries(entity.getRecurrenceSeriesId());
            return deletedCount > 0;
        }
        entity.setStatus(STATUS_DELETED);
        entity.setUpdatedAt(LocalDateTime.now());
        boolean deleted = calendarEventMapper.updateById(entity) > 0;
        if (deleted) {
            reminderJobService.deleteForEvent(eventId);
        }
        return deleted;
    }

    @Override
    public ConflictResultVO checkConflict(ConflictCheckRequest request) {
        validateConflictRequest(request);
        Long userId = resolveUserId(request.getUserId());

        List<CalendarEventEntity> conflictEvents = listEventsForRange(
                userId,
                request.getStartTime(),
                request.getEndTime(),
                request.getExcludeEventId()
        );

        ConflictResultVO result = new ConflictResultVO();
        result.setHasConflict(!conflictEvents.isEmpty());
        result.setConflictEvents(conflictEvents.stream().map(this::toVO).collect(Collectors.toList()));
        if (!conflictEvents.isEmpty()) {
            // 冲突时才计算推荐空闲段，减少普通检测的数据库和内存开销。
            int duration = (int) java.time.Duration.between(request.getStartTime(), request.getEndTime()).toMinutes();
            result.setSuggestedSlots(findFreeSlotsInternal(
                    userId,
                    request.getStartTime().toLocalDate(),
                    Math.max(duration, 1),
                    maxFreeSlotCount,
                    request.getExcludeEventId()
            ));
        }
        return result;
    }

    @Override
    public List<FreeSlotVO> findFreeSlots(FreeSlotRequest request) {
        FreeSlotRequest safeRequest = request == null ? new FreeSlotRequest() : request;
        return findFreeSlotsInternal(
                resolveUserId(safeRequest.getUserId()),
                safeRequest.getDate() == null ? LocalDate.now() : safeRequest.getDate(),
                safeRequest.getDurationMinutes() == null ? defaultDurationMinutes : safeRequest.getDurationMinutes(),
                safeRequest.getMaxCount() == null ? maxFreeSlotCount : safeRequest.getMaxCount(),
                null
        );
    }

    @Override
    public List<CalendarEventVO> findCandidateEvents(EventResolveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("事件定位参数不能为空");
        }
        if (request.getEventId() != null) {
            return java.util.Collections.singletonList(toVO(getActiveEvent(
                    request.getEventId(),
                    resolveUserId(request.getUserId())
            )));
        }
        QueryEventRequest query = new QueryEventRequest();
        query.setUserId(request.getUserId());
        query.setStartTime(request.getRangeStart());
        query.setEndTime(request.getRangeEnd());
        query.setKeyword(request.getTitleKeyword());
        return queryEvents(query);
    }

    private CalendarEventVO createRecurringEvents(CreateEventRequest request, Long userId) {
        String recurrenceType = request.getRecurrenceType().trim().toUpperCase();
        int interval = request.getRecurrenceInterval() == null ? 1 : request.getRecurrenceInterval();
        int requestedCount = request.getRecurrenceCount() == null ? recurrenceDefaultCount : request.getRecurrenceCount();
        int count = Math.min(requestedCount, recurrenceMaxCount);
        if (interval <= 0) {
            throw new IllegalArgumentException("recurrenceInterval 必须大于 0");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("recurrenceCount 必须大于 0");
        }

        String seriesId = "series_" + UUID.randomUUID().toString().replace("-", "");
        String baseIdempotencyKey = StringUtils.hasText(request.getIdempotencyKey())
                ? request.getIdempotencyKey().trim()
                : "event_" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        List<CalendarEventEntity> occurrences = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            LocalDateTime startTime = addRecurrence(request.getStartTime(), recurrenceType, interval, index);
            if (request.getRecurrenceUntil() != null && startTime.toLocalDate().isAfter(request.getRecurrenceUntil())) {
                break;
            }
            LocalDateTime endTime = startTime.plusMinutes(
                    java.time.Duration.between(request.getStartTime(), request.getEndTime()).toMinutes()
            );
            CalendarEventEntity occurrence = newEventEntity(request, userId, startTime, endTime);
            occurrence.setIdempotencyKey(index == 0 ? baseIdempotencyKey : baseIdempotencyKey + "_" + index);
            occurrence.setRecurrenceSeriesId(seriesId);
            occurrence.setRecurrenceIndex(index);
            occurrence.setCreatedAt(now);
            occurrence.setUpdatedAt(now);
            occurrences.add(occurrence);
        }
        if (occurrences.isEmpty()) {
            throw new IllegalArgumentException("重复日程没有可创建的实例");
        }

        List<CalendarEventEntity> busyEvents = listEventsForRange(
                userId,
                occurrences.get(0).getStartTime(),
                occurrences.get(occurrences.size() - 1).getEndTime(),
                null
        );
        for (CalendarEventEntity occurrence : occurrences) {
            for (CalendarEventEntity busyEvent : busyEvents) {
                if (occurrence.getStartTime().isBefore(busyEvent.getEndTime())
                        && occurrence.getEndTime().isAfter(busyEvent.getStartTime())) {
                    throw new IllegalStateException("重复日程中的部分时间段已有日程冲突");
                }
            }
        }

        RecurrenceSeriesEntity series = new RecurrenceSeriesEntity();
        series.setSeriesId(seriesId);
        series.setUserId(userId);
        series.setTitle(request.getTitle().trim());
        series.setRecurrenceType(recurrenceType);
        series.setIntervalValue(interval);
        series.setTotalCount(occurrences.size());
        series.setUntilDate(request.getRecurrenceUntil());
        series.setStatus(STATUS_ACTIVE);
        series.setCreatedAt(now);
        series.setUpdatedAt(now);
        recurrenceSeriesMapper.insert(series);

        calendarEventMapper.insertBatch(occurrences);
        List<CalendarEventEntity> created = calendarEventMapper.selectList(
                Wrappers.lambdaQuery(CalendarEventEntity.class)
                        .eq(CalendarEventEntity::getRecurrenceSeriesId, seriesId)
                        .orderByAsc(CalendarEventEntity::getRecurrenceIndex)
        );
        reminderJobService.createForEvents(created, request.getEmailReceiver(), request.getEmailContent());
        calendarEmailService.notifyEventsCreatedAfterCommit(created, request.getEmailReceiver());
        return toVO(created.get(0));
    }

    private CalendarEventEntity newEventEntity(
            CreateEventRequest request,
            Long userId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        CalendarEventEntity entity = new CalendarEventEntity();
        entity.setUserId(userId);
        entity.setTitle(request.getTitle().trim());
        entity.setStartTime(startTime);
        entity.setEndTime(endTime);
        entity.setLocation(trimToNull(request.getLocation()));
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setMeetingUrl(trimToNull(request.getMeetingUrl()));
        entity.setMeetingProvider(trimToNull(request.getMeetingProvider()));
        entity.setMeetingCode(trimToNull(request.getMeetingCode()));
        entity.setReminderMinutes(request.getReminderMinutes());
        entity.setSource(StringUtils.hasText(request.getSource()) ? request.getSource().trim() : SOURCE_API);
        entity.setStatus(STATUS_ACTIVE);
        entity.setSourceTaskId(trimToNull(request.getSourceTaskId()));
        return entity;
    }

    private LocalDateTime addRecurrence(LocalDateTime base, String recurrenceType, int interval, int index) {
        long amount = (long) interval * index;
        if (RECURRENCE_DAILY.equals(recurrenceType)) {
            return base.plusDays(amount);
        }
        if (RECURRENCE_WEEKLY.equals(recurrenceType)) {
            return base.plusWeeks(amount);
        }
        if (RECURRENCE_MONTHLY.equals(recurrenceType)) {
            return base.plusMonths(amount);
        }
        throw new IllegalArgumentException("不支持的重复类型: " + recurrenceType);
    }

    private boolean isRecurring(CreateEventRequest request) {
        return StringUtils.hasText(request.getRecurrenceType())
                && !RECURRENCE_NONE.equalsIgnoreCase(request.getRecurrenceType().trim());
    }

    private CalendarEventEntity findByIdempotencyKey(Long userId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        return calendarEventMapper.selectOne(Wrappers.lambdaQuery(CalendarEventEntity.class)
                .eq(CalendarEventEntity::getUserId, userId)
                .eq(CalendarEventEntity::getIdempotencyKey, idempotencyKey.trim())
                .ne(CalendarEventEntity::getStatus, STATUS_DELETED)
                .last("LIMIT 1"));
    }

    private CalendarEventEntity getActiveEvent(Long eventId, Long userId) {
        CalendarEventEntity entity = calendarEventMapper.selectOne(Wrappers.lambdaQuery(CalendarEventEntity.class)
                .eq(CalendarEventEntity::getId, eventId)
                .eq(CalendarEventEntity::getUserId, userId)
                .ne(CalendarEventEntity::getStatus, STATUS_DELETED)
                .last("LIMIT 1"));
        if (entity == null) {
            throw new IllegalArgumentException("未找到日程或日程已删除");
        }
        return entity;
    }

    private List<CalendarEventEntity> listEventsForRange(
            Long userId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long excludeEventId
    ) {
        // 重叠判断：newStart < existEnd && newEnd > existStart。
        LambdaQueryWrapper<CalendarEventEntity> wrapper = Wrappers.lambdaQuery(CalendarEventEntity.class)
                .eq(CalendarEventEntity::getUserId, userId)
                .ne(CalendarEventEntity::getStatus, STATUS_DELETED)
                .lt(CalendarEventEntity::getStartTime, endTime)
                .gt(CalendarEventEntity::getEndTime, startTime);
        if (excludeEventId != null) {
            wrapper.ne(CalendarEventEntity::getId, excludeEventId);
        }
        wrapper.orderByAsc(CalendarEventEntity::getStartTime);
        return calendarEventMapper.selectList(wrapper);
    }

    private List<FreeSlotVO> findFreeSlotsInternal(
            Long userId,
            LocalDate date,
            Integer durationMinutes,
            Integer maxCount,
            Long excludeEventId
    ) {
        if (durationMinutes == null || durationMinutes <= 0) {
            throw new IllegalArgumentException("durationMinutes 必须大于 0");
        }
        int limit = maxCount == null || maxCount <= 0 ? maxFreeSlotCount : maxCount;
        LocalDateTime dayStart = date.atTime(LocalTime.parse(workStartTime));
        LocalDateTime dayEnd = date.atTime(LocalTime.parse(workEndTime));

        List<CalendarEventEntity> busyEvents = listEventsForRange(userId, dayStart, dayEnd, excludeEventId);
        busyEvents.sort(Comparator.comparing(CalendarEventEntity::getStartTime));

        // 用 cursor 从工作日开始时间向后扫描，遇到忙碌事件就跳到该事件结束时间。
        List<FreeSlotVO> slots = new ArrayList<>();
        LocalDateTime cursor = dayStart;
        for (CalendarEventEntity event : busyEvents) {
            if (slots.size() >= limit) {
                break;
            }
            LocalDateTime busyStart = event.getStartTime().isBefore(dayStart) ? dayStart : event.getStartTime();
            LocalDateTime busyEnd = event.getEndTime().isAfter(dayEnd) ? dayEnd : event.getEndTime();
            if (!cursor.plusMinutes(durationMinutes).isAfter(busyStart)) {
                slots.add(newFreeSlot(cursor, cursor.plusMinutes(durationMinutes)));
            }
            if (busyEnd.isAfter(cursor)) {
                cursor = busyEnd;
            }
        }

        if (slots.size() < limit && !cursor.plusMinutes(durationMinutes).isAfter(dayEnd)) {
            slots.add(newFreeSlot(cursor, cursor.plusMinutes(durationMinutes)));
        }
        return slots;
    }

    private FreeSlotVO newFreeSlot(LocalDateTime startTime, LocalDateTime endTime) {
        FreeSlotVO slot = new FreeSlotVO();
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);
        return slot;
    }

    private void validateCreateRequest(CreateEventRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("创建日程参数不能为空");
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new IllegalArgumentException("title 不能为空");
        }
        validateTimeRange(request.getStartTime(), request.getEndTime());
    }

    private void validateConflictRequest(ConflictCheckRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("冲突检测参数不能为空");
        }
        validateTimeRange(request.getStartTime(), request.getEndTime());
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null) {
            throw new IllegalArgumentException("startTime 不能为空");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("endTime 不能为空");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime 必须晚于 startTime");
        }
    }

    private Long resolveUserId(Long userId) {
        return userId == null ? defaultUserId : userId;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private CalendarEventVO toVO(CalendarEventEntity entity) {
        CalendarEventVO vo = new CalendarEventVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setTitle(entity.getTitle());
        vo.setStartTime(entity.getStartTime());
        vo.setEndTime(entity.getEndTime());
        vo.setLocation(entity.getLocation());
        vo.setDescription(entity.getDescription());
        vo.setMeetingUrl(entity.getMeetingUrl());
        vo.setMeetingProvider(entity.getMeetingProvider());
        vo.setMeetingCode(entity.getMeetingCode());
        vo.setReminderMinutes(entity.getReminderMinutes());
        vo.setSource(entity.getSource());
        vo.setStatus(entity.getStatus());
        vo.setRecurrenceSeriesId(entity.getRecurrenceSeriesId());
        vo.setRecurrenceIndex(entity.getRecurrenceIndex());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
