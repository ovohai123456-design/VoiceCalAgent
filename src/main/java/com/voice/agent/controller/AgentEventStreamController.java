package com.voice.agent.controller;

import com.voice.agent.auth.AuthContext;
import com.voice.agent.stream.AgentEventStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/events")
public class AgentEventStreamController {
    private final AgentEventStreamService eventStreamService;

    public AgentEventStreamController(AgentEventStreamService eventStreamService) {
        this.eventStreamService = eventStreamService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return eventStreamService.subscribe(AuthContext.requireUserId(request));
    }
}
