package com.voice.agent.agent;

import com.voice.agent.model.dto.ConflictCheckRequest;
import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.dto.QueryEventRequest;
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

    public CalendarAgent(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    public PreparedCreateAction prepareCreateAction(CreateEventRequest request, String taskId) {
        request.setTaskId(taskId);
        if (!StringUtils.hasText(request.getIdempotencyKey())) {
            request.setIdempotencyKey("event_" + UUID.randomUUID().toString().replace("-", ""));
        }

        ConflictCheckRequest conflictRequest = new ConflictCheckRequest();
        conflictRequest.setUserId(request.getUserId());
        conflictRequest.setStartTime(request.getStartTime());
        conflictRequest.setEndTime(request.getEndTime());
        ConflictResultVO conflict = calendarService.checkConflict(conflictRequest);

        boolean adjustedByConflict = false;
        if (Boolean.TRUE.equals(conflict.getHasConflict()) && !conflict.getSuggestedSlots().isEmpty()) {
            FreeSlotVO suggested = conflict.getSuggestedSlots().get(0);
            request.setStartTime(suggested.getStartTime());
            request.setEndTime(suggested.getEndTime());
            adjustedByConflict = true;
        } else if (Boolean.TRUE.equals(conflict.getHasConflict())) {
            throw new IllegalStateException("目标时间段已有冲突，暂时没有找到可推荐的空闲时间");
        }

        PreparedCreateAction action = new PreparedCreateAction();
        action.setCreateEventRequest(request);
        action.setConflictResult(conflict);
        action.setAdjustedByConflict(adjustedByConflict);
        action.setReplyText(buildCreateConfirmText(request, adjustedByConflict));
        return action;
    }

    public CalendarEventVO executeCreate(CreateEventRequest request) {
        return calendarService.createEvent(request);
    }

    public List<CalendarEventVO> query(QueryEventRequest request) {
        return calendarService.queryEvents(request);
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
        return "已创建日程「" + event.getTitle() + "」，时间是 "
                + event.getStartTime().format(DISPLAY_TIME_FORMATTER)
                + " 到 "
                + event.getEndTime().format(DISPLAY_TIME_FORMATTER)
                + "。";
    }

    private String buildCreateConfirmText(CreateEventRequest request, boolean adjustedByConflict) {
        String prefix = adjustedByConflict ? "原时间有冲突，我建议改到可用时间。" : "";
        return prefix + "我将创建「" + request.getTitle() + "」，时间是 "
                + request.getStartTime().format(DISPLAY_TIME_FORMATTER)
                + " 到 "
                + request.getEndTime().format(DISPLAY_TIME_FORMATTER)
                + "，是否确认？";
    }

    public static class PreparedCreateAction {
        private CreateEventRequest createEventRequest;
        private ConflictResultVO conflictResult;
        private Boolean adjustedByConflict;
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

        public Boolean getAdjustedByConflict() {
            return adjustedByConflict;
        }

        public void setAdjustedByConflict(Boolean adjustedByConflict) {
            this.adjustedByConflict = adjustedByConflict;
        }

        public String getReplyText() {
            return replyText;
        }

        public void setReplyText(String replyText) {
            this.replyText = replyText;
        }
    }
}
