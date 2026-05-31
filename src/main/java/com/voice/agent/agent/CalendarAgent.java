package com.voice.agent.agent;

import com.voice.agent.model.dto.ConflictCheckRequest;
import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.dto.DeleteEventActionPayload;
import com.voice.agent.model.dto.EventResolveRequest;
import com.voice.agent.model.dto.QueryEventRequest;
import com.voice.agent.model.dto.UpdateEventActionPayload;
import com.voice.agent.model.dto.UpdateEventRequest;
import com.voice.agent.model.vo.CalendarEventVO;
import com.voice.agent.model.vo.ConflictResultVO;
import com.voice.agent.model.vo.FreeSlotVO;
import com.voice.agent.service.CalendarService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 日历业务 Agent。
 *
 * <p>RouterAgent 只负责判断“该交给谁”，CalendarAgent 负责日历领域内的冲突检测、查询和执行。</p>
 */
@Component
public class CalendarAgent {
    private static final DateTimeFormatter DISPLAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CalendarService calendarService;
    private final EventResolveService eventResolveService;

    public CalendarAgent(CalendarService calendarService, EventResolveService eventResolveService) {
        this.calendarService = calendarService;
        this.eventResolveService = eventResolveService;
    }

    public PreparedCreateAction prepareCreateAction(CreateEventRequest request, String taskId) {
        request.setSourceTaskId(taskId);
        if (!StringUtils.hasText(request.getIdempotencyKey())) {
            request.setIdempotencyKey("event_" + UUID.randomUUID().toString().replace("-", ""));
        }

        ConflictCheckRequest conflictRequest = new ConflictCheckRequest();
        conflictRequest.setUserId(request.getUserId());
        conflictRequest.setStartTime(request.getStartTime());
        conflictRequest.setEndTime(request.getEndTime());
        ConflictResultVO conflict = calendarService.checkConflict(conflictRequest);

        if (Boolean.TRUE.equals(conflict.getHasConflict()) && conflict.getSuggestedSlots().isEmpty()) {
            throw new IllegalStateException("目标时间段已有冲突，暂时没有找到可推荐的空闲时间");
        }

        PreparedCreateAction action = new PreparedCreateAction();
        action.setCreateEventRequest(request);
        action.setConflictResult(conflict);
        action.setRequiresSlotSelection(Boolean.TRUE.equals(conflict.getHasConflict()));
        action.setReplyText(Boolean.TRUE.equals(conflict.getHasConflict())
                ? buildConflictReplyText(request, conflict.getSuggestedSlots())
                : buildCreateConfirmText(request));
        return action;
    }

    public CreateEventRequest selectSuggestedSlot(CreateEventRequest request, Integer slotIndex) {
        if (slotIndex == null || slotIndex < 0) {
            throw new IllegalArgumentException("请选择有效的候选时间");
        }
        ConflictCheckRequest conflictRequest = new ConflictCheckRequest();
        conflictRequest.setUserId(request.getUserId());
        conflictRequest.setStartTime(request.getStartTime());
        conflictRequest.setEndTime(request.getEndTime());
        ConflictResultVO conflict = calendarService.checkConflict(conflictRequest);
        if (!Boolean.TRUE.equals(conflict.getHasConflict())) {
            return request;
        }
        if (slotIndex >= conflict.getSuggestedSlots().size()) {
            throw new IllegalArgumentException("候选时间不存在，请重新发起创建");
        }
        FreeSlotVO selected = conflict.getSuggestedSlots().get(slotIndex);
        request.setStartTime(selected.getStartTime());
        request.setEndTime(selected.getEndTime());
        return request;
    }

    public CalendarEventVO executeCreate(CreateEventRequest request) {
        return calendarService.createPreparedEvent(request);
    }

    public List<CalendarEventVO> query(QueryEventRequest request) {
        return calendarService.queryEvents(request);
    }

    public PreparedUpdateAction prepareUpdateAction(
            EventResolveRequest resolveRequest,
            UpdateEventRequest updateRequest,
            String taskId
    ) {
        CalendarEventVO event = eventResolveService.resolveSingle(resolveRequest);
        return prepareUpdateAction(event, updateRequest, taskId);
    }

    public PreparedUpdateAction prepareUpdateAction(
            CalendarEventVO event,
            UpdateEventRequest updateRequest,
            String taskId
    ) {
        updateRequest.setUserId(event.getUserId());
        updateRequest.setSourceTaskId(taskId);

        ConflictCheckRequest conflictRequest = new ConflictCheckRequest();
        conflictRequest.setUserId(event.getUserId());
        conflictRequest.setStartTime(updateRequest.getStartTime() == null ? event.getStartTime() : updateRequest.getStartTime());
        conflictRequest.setEndTime(updateRequest.getEndTime() == null ? event.getEndTime() : updateRequest.getEndTime());
        conflictRequest.setExcludeEventId(event.getId());
        ConflictResultVO conflict = calendarService.checkConflict(conflictRequest);
        if (Boolean.TRUE.equals(conflict.getHasConflict())) {
            throw new IllegalStateException("修改后的时间段已有日程冲突");
        }

        UpdateEventActionPayload payload = new UpdateEventActionPayload();
        payload.setEventId(event.getId());
        payload.setUpdateRequest(updateRequest);

        PreparedUpdateAction action = new PreparedUpdateAction();
        action.setPayload(payload);
        action.setOriginalEvent(event);
        action.setReplyText(buildUpdateConfirmText(event, updateRequest));
        return action;
    }

    public PreparedDeleteAction prepareDeleteAction(EventResolveRequest resolveRequest) {
        CalendarEventVO event = eventResolveService.resolveSingle(resolveRequest);
        return prepareDeleteAction(event);
    }

    public PreparedDeleteAction prepareDeleteAction(CalendarEventVO event) {
        DeleteEventActionPayload payload = new DeleteEventActionPayload();
        payload.setEventId(event.getId());
        payload.setUserId(event.getUserId());
        payload.setTitle(event.getTitle());

        PreparedDeleteAction action = new PreparedDeleteAction();
        action.setPayload(payload);
        action.setOriginalEvent(event);
        action.setReplyText("我将删除日程「" + event.getTitle() + "」，时间是 "
                + event.getStartTime().format(DISPLAY_TIME_FORMATTER) + "。请回复确认或取消。");
        return action;
    }

    public CalendarEventVO executeUpdate(UpdateEventActionPayload payload) {
        return calendarService.updatePreparedEvent(payload.getEventId(), payload.getUpdateRequest());
    }

    public Boolean executeDelete(DeleteEventActionPayload payload) {
        return calendarService.deleteEvent(payload.getEventId(), payload.getUserId());
    }

    public String buildQueryReply(List<CalendarEventVO> events) {
        if (events == null || events.isEmpty()) {
            return "没有查询到对应时间段的日程。";
        }
        StringBuilder builder = new StringBuilder("查询到 ").append(events.size()).append(" 个日程：");
        for (CalendarEventVO event : events) {
            builder.append(event.getStartTime().format(DISPLAY_TIME_FORMATTER))
                    .append(" ")
                    .append(event.getTitle())
                    .append("；");
        }
        return builder.toString();
    }

    public String buildCreateSuccessText(CalendarEventVO event) {
        String recurringText = StringUtils.hasText(event.getRecurrenceSeriesId()) ? "，并已生成重复日程" : "";
        return "已创建日程「" + event.getTitle() + "」，时间是 "
                + event.getStartTime().format(DISPLAY_TIME_FORMATTER)
                + " 到 "
                + event.getEndTime().format(DISPLAY_TIME_FORMATTER)
                + recurringText
                + "。";
    }

    public String buildUpdateSuccessText(CalendarEventVO event) {
        return "已更新日程「" + event.getTitle() + "」，时间是 "
                + event.getStartTime().format(DISPLAY_TIME_FORMATTER)
                + " 到 "
                + event.getEndTime().format(DISPLAY_TIME_FORMATTER)
                + "。";
    }

    public String buildDeleteSuccessText(DeleteEventActionPayload payload) {
        return "已删除日程「" + payload.getTitle() + "」。";
    }

    public String buildEventSelectionReply(List<CalendarEventVO> candidates) {
        StringBuilder builder = new StringBuilder("找到多个匹配日程，请回复要操作第几个：");
        for (int index = 0; index < candidates.size(); index++) {
            CalendarEventVO candidate = candidates.get(index);
            builder.append(index + 1)
                    .append(". ")
                    .append(candidate.getStartTime().format(DISPLAY_TIME_FORMATTER))
                    .append(" ")
                    .append(candidate.getTitle())
                    .append("；");
        }
        return builder.append("也可以回复取消。").toString();
    }

    private String buildUpdateConfirmText(CalendarEventVO event, UpdateEventRequest request) {
        return "我将把日程「" + event.getTitle() + "」调整为 "
                + request.getStartTime().format(DISPLAY_TIME_FORMATTER)
                + " 到 "
                + request.getEndTime().format(DISPLAY_TIME_FORMATTER)
                + "。请回复确认或取消。";
    }

    private String buildCreateConfirmText(CreateEventRequest request) {
        return "我将创建「" + request.getTitle() + "」，时间是 "
                + request.getStartTime().format(DISPLAY_TIME_FORMATTER)
                + " 到 "
                + request.getEndTime().format(DISPLAY_TIME_FORMATTER)
                + "。请回复确认或取消。";
    }

    private String buildConflictReplyText(CreateEventRequest request, List<FreeSlotVO> slots) {
        StringBuilder builder = new StringBuilder("「")
                .append(request.getTitle())
                .append("」原时间已有冲突，请选择一个可用时间：");
        for (int index = 0; index < slots.size(); index++) {
            FreeSlotVO slot = slots.get(index);
            builder.append(index + 1)
                    .append(". ")
                    .append(slot.getStartTime().format(DISPLAY_TIME_FORMATTER))
                    .append(" 到 ")
                    .append(slot.getEndTime().format(DISPLAY_TIME_FORMATTER))
                    .append("；");
        }
        return builder.append("请回复第几个，或回复取消。").toString();
    }

    public static class PreparedCreateAction {
        private CreateEventRequest createEventRequest;
        private ConflictResultVO conflictResult;
        private Boolean requiresSlotSelection;
        private String replyText;

        public CreateEventRequest getCreateEventRequest() {
            return createEventRequest;
        }

        public void setCreateEventRequest(CreateEventRequest createEventRequest) {
            this.createEventRequest = createEventRequest;
        }

        public ConflictResultVO getConflictResult() {
            return conflictResult;
        }

        public void setConflictResult(ConflictResultVO conflictResult) {
            this.conflictResult = conflictResult;
        }

        public Boolean getRequiresSlotSelection() {
            return requiresSlotSelection;
        }

        public void setRequiresSlotSelection(Boolean requiresSlotSelection) {
            this.requiresSlotSelection = requiresSlotSelection;
        }

        public String getReplyText() {
            return replyText;
        }

        public void setReplyText(String replyText) {
            this.replyText = replyText;
        }
    }

    public static class PreparedUpdateAction {
        private UpdateEventActionPayload payload;
        private CalendarEventVO originalEvent;
        private String replyText;

        public UpdateEventActionPayload getPayload() {
            return payload;
        }

        public void setPayload(UpdateEventActionPayload payload) {
            this.payload = payload;
        }

        public CalendarEventVO getOriginalEvent() {
            return originalEvent;
        }

        public void setOriginalEvent(CalendarEventVO originalEvent) {
            this.originalEvent = originalEvent;
        }

        public String getReplyText() {
            return replyText;
        }

        public void setReplyText(String replyText) {
            this.replyText = replyText;
        }
    }

    public static class PreparedDeleteAction {
        private DeleteEventActionPayload payload;
        private CalendarEventVO originalEvent;
        private String replyText;

        public DeleteEventActionPayload getPayload() {
            return payload;
        }

        public void setPayload(DeleteEventActionPayload payload) {
            this.payload = payload;
        }

        public CalendarEventVO getOriginalEvent() {
            return originalEvent;
        }

        public void setOriginalEvent(CalendarEventVO originalEvent) {
            this.originalEvent = originalEvent;
        }

        public String getReplyText() {
            return replyText;
        }

        public void setReplyText(String replyText) {
            this.replyText = replyText;
        }
    }
}
