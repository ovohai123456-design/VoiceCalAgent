package com.voice.agent.controller;

import com.voice.agent.auth.AuthContext;
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

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * 日历 REST 接口。
 *
 * <p>这一层只负责 HTTP 参数组装和统一响应包装，业务规则放在 CalendarService 中。
 * 时间参数统一使用 yyyy-MM-dd HH:mm:ss，和 application.yml 的 Jackson 配置保持一致。</p>
 */
@RestController
@RequestMapping("/api/calendar")
public class CalendarController {
    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    /**
     * 创建事件。
     *
     * <p>创建事件时，会检查时间冲突，如果冲突则返回409错误。</p>
     */
    @PostMapping("/events")
    public ApiResponse<CalendarEventVO> createEvent(@RequestBody CreateEventRequest request, HttpServletRequest httpRequest) {
        request.setUserId(AuthContext.requireUserId(httpRequest));
        return handle(() -> calendarService.createEvent(request));
    }

    /**
     * 查询事件。
     *
     * <p>查询事件时，会返回所有符合条件的事件，包括用户自己创建的事件和被共享的事件。</p>
     */
    @GetMapping("/events")
    public ApiResponse<List<CalendarEventVO>> queryEvents(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            HttpServletRequest httpRequest
    ) {
        // GET 查询参数不适合直接绑定复杂 DTO，这里手动组装，避免时间格式转换不稳定。
        QueryEventRequest request = new QueryEventRequest();
        request.setUserId(AuthContext.requireUserId(httpRequest));
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setKeyword(keyword);
        request.setStatus(status);
        return handle(() -> calendarService.queryEvents(request));
    }

    /**
     * 更新事件。
     *
     * <p>更新事件时，会检查时间冲突，如果冲突则返回409错误。</p>
     */
    @PutMapping("/events/{eventId}")
    public ApiResponse<CalendarEventVO> updateEvent(
            @PathVariable Long eventId,
            @RequestBody UpdateEventRequest request,
            HttpServletRequest httpRequest
    ) {
        request.setUserId(AuthContext.requireUserId(httpRequest));
        return handle(() -> calendarService.updateEvent(eventId, request));
    }

    /**
     * 删除事件。
     *
     * <p>删除事件时，会检查事件是否属于当前用户，如果不是则返回403错误。</p>
     */
    @DeleteMapping("/events/{eventId}")
    public ApiResponse<Boolean> deleteEvent(
            @PathVariable Long eventId,
            @RequestParam(required = false, defaultValue = "SINGLE") String scope,
            HttpServletRequest httpRequest
    ) {
        return handle(() -> calendarService.deleteEvent(eventId, AuthContext.requireUserId(httpRequest), scope));
    }

    /**
     * 检查时间冲突。
     *
     * <p>检查时间冲突时，会返回所有冲突的事件。</p>
     */
    @PostMapping("/conflict/check")
    public ApiResponse<ConflictResultVO> checkConflict(@RequestBody ConflictCheckRequest request, HttpServletRequest httpRequest) {
        request.setUserId(AuthContext.requireUserId(httpRequest));
        return handle(() -> calendarService.checkConflict(request));
    }

    /**
     * 查找空闲时间段。
     *
     * <p>查找空闲时间段时，会返回所有空闲时间段。</p>
     */
    @PostMapping("/free-slots")
    public ApiResponse<List<FreeSlotVO>> findFreeSlots(@RequestBody FreeSlotRequest request, HttpServletRequest httpRequest) {
        request.setUserId(AuthContext.requireUserId(httpRequest));
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
