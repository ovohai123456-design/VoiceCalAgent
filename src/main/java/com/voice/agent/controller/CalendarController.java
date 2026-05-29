package com.voice.agent.controller;

import com.voice.agent.model.dto.ConflictCheckRequest;
import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.dto.FreeSlotRequest;
import com.voice.agent.model.dto.QueryEventRequest;
import com.voice.agent.model.dto.UpdateEventRequest;
import com.voice.agent.model.vo.ApiResponse;
import com.voice.agent.model.vo.CalendarEventVO;
import com.voice.agent.model.vo.ConflictResultVO;
import com.voice.agent.model.vo.FreeSlotVO;
import com.voice.agent.service.CalendarService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {
    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @PostMapping("/events")
    public ApiResponse<CalendarEventVO> createEvent(@RequestBody CreateEventRequest request) {
        return handle(() -> calendarService.createEvent(request));
    }

    @GetMapping("/events")
    public ApiResponse<List<CalendarEventVO>> queryEvents(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        QueryEventRequest request = new QueryEventRequest();
        request.setUserId(userId);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setKeyword(keyword);
        request.setStatus(status);
        return handle(() -> calendarService.queryEvents(request));
    }

    @PutMapping("/events/{eventId}")
    public ApiResponse<CalendarEventVO> updateEvent(
            @PathVariable Long eventId,
            @RequestBody UpdateEventRequest request
    ) {
        return handle(() -> calendarService.updateEvent(eventId, request));
    }

    @DeleteMapping("/events/{eventId}")
    public ApiResponse<Boolean> deleteEvent(
            @PathVariable Long eventId,
            @RequestParam(required = false) Long userId
    ) {
        return handle(() -> calendarService.deleteEvent(eventId, userId));
    }

    @PostMapping("/conflict/check")
    public ApiResponse<ConflictResultVO> checkConflict(@RequestBody ConflictCheckRequest request) {
        return handle(() -> calendarService.checkConflict(request));
    }

    @PostMapping("/free-slots")
    public ApiResponse<List<FreeSlotVO>> findFreeSlots(@RequestBody FreeSlotRequest request) {
        return handle(() -> calendarService.findFreeSlots(request));
    }

    private <T> ApiResponse<T> handle(Supplier<T> supplier) {
        try {
            return ApiResponse.ok(supplier.get());
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail("BAD_REQUEST", e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail("CONFLICT", e.getMessage());
        }
    }
}
