package com.voice.agent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.voice.agent.model.dto.ConflictCheckRequest;
import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.dto.EventResolveRequest;
import com.voice.agent.model.dto.QueryEventRequest;
import com.voice.agent.model.dto.UpdateEventRequest;
import com.voice.agent.service.CalendarService;
import com.voice.agent.service.ContactService;
import com.voice.agent.model.entity.ContactEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 注册原生工具，用于 LLM 模型调用。
 */
@Component
public class NativeToolRegistry {
    private final Map<String, NativeTool> tools = new LinkedHashMap<>();

    public NativeToolRegistry() {
    }

    @Autowired
    public NativeToolRegistry(CalendarService calendarService, ContactService contactService, ObjectMapper objectMapper) {
        ObjectMapper toolObjectMapper = objectMapper.copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        register("calendar.create", arguments ->
                calendarService.createEvent(toolObjectMapper.convertValue(arguments, CreateEventRequest.class)));
        register("calendar.query", arguments ->
                calendarService.queryEvents(toolObjectMapper.convertValue(arguments, QueryEventRequest.class)));
        register("calendar.update", arguments -> {
            Long eventId = asLong(arguments.get("event_id"));
            Map<String, Object> requestArguments = new LinkedHashMap<>(arguments);
            requestArguments.remove("event_id");
            return calendarService.updateEvent(eventId, toolObjectMapper.convertValue(requestArguments, UpdateEventRequest.class));
        });
        register("calendar.delete", arguments ->
                calendarService.deleteEvent(
                        asLong(arguments.get("event_id")),
                        asLong(arguments.get("user_id")),
                        String.valueOf(arguments.getOrDefault("scope", "SINGLE"))
                ));
        register("calendar.conflict_check", arguments ->
                calendarService.checkConflict(toolObjectMapper.convertValue(arguments, ConflictCheckRequest.class)));
        register("calendar.resolve_event", arguments ->
                calendarService.findCandidateEvents(toolObjectMapper.convertValue(arguments, EventResolveRequest.class)));
        register("contact.query", arguments -> {
            ContactEntity contact = contactService.resolve(
                    asLong(arguments.get("user_id")),
                    String.valueOf(arguments.get("keyword"))
            );
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", contact.getId());
            result.put("name", contact.getName());
            result.put("phone", contact.getPhone());
            result.put("email", contact.getEmail());
            return result;
        });
    }

    public void register(String toolKey, NativeTool tool) {
        if (!StringUtils.hasText(toolKey) || tool == null) {
            throw new IllegalArgumentException("toolKey and tool must not be empty");
        }
        tools.put(toolKey, tool);
    }

    public boolean contains(String toolKey) {
        return StringUtils.hasText(toolKey) && tools.containsKey(toolKey);
    }

    public Object execute(String toolKey, Map<String, Object> arguments) {
        NativeTool tool = tools.get(toolKey);
        if (tool == null) {
            throw new IllegalArgumentException("Native tool is not registered: " + toolKey);
        }
        return tool.execute(arguments);
    }

    @FunctionalInterface
    public interface NativeTool {
        Object execute(Map<String, Object> arguments);
    }

    private static Long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }
}
