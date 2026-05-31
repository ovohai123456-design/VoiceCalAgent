package com.voice.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voice.agent.agent.AgentApplicationService;
import com.voice.agent.agent.CommandWorkflowService;
import com.voice.agent.auth.AuthContext;
import com.voice.agent.auth.AuthTokenService;
import com.voice.agent.mapper.UserMapper;
import com.voice.agent.controller.AgentController;
import com.voice.agent.controller.CalendarController;
import com.voice.agent.controller.WorkflowController;
import com.voice.agent.controller.ReminderController;
import com.voice.agent.controller.SkillController;
import com.voice.agent.controller.ContactController;
import com.voice.agent.controller.UserPreferenceController;
import com.voice.agent.model.dto.AgentCancelRequest;
import com.voice.agent.model.dto.AgentConfirmRequest;
import com.voice.agent.model.dto.AgentExecuteRequest;
import com.voice.agent.model.dto.AgentSelectSlotRequest;
import com.voice.agent.model.dto.AgentSelectEventRequest;
import com.voice.agent.model.dto.CreateEventRequest;
import com.voice.agent.model.dto.QueryEventRequest;
import com.voice.agent.model.vo.AgentResponse;
import com.voice.agent.model.vo.ExecutionLogVO;
import com.voice.agent.model.vo.CalendarEventVO;
import com.voice.agent.model.vo.ReminderJobVO;
import com.voice.agent.model.entity.ContactEntity;
import com.voice.agent.model.entity.UserPreferenceEntity;
import com.voice.agent.service.CalendarService;
import com.voice.agent.service.ReminderJobService;
import com.voice.agent.service.ContactService;
import com.voice.agent.service.UserPreferenceService;
import com.voice.agent.skill.SkillDefinition;
import com.voice.agent.skill.SkillRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AgentController.class,
        CalendarController.class,
        WorkflowController.class,
        ReminderController.class,
        SkillController.class,
        ContactController.class,
        UserPreferenceController.class
})
@AutoConfigureMockMvc(addFilters = false)
class AgentRouteTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AgentApplicationService agentApplicationService;

    @MockBean
    private CalendarService calendarService;

    @MockBean
    private CommandWorkflowService commandWorkflowService;

    @MockBean
    private ReminderJobService reminderJobService;

    @MockBean
    private SkillRegistry skillRegistry;

    @MockBean
    private ContactService contactService;

    @MockBean
    private UserPreferenceService userPreferenceService;

    @MockBean
    private AuthTokenService authTokenService;

    @MockBean
    private UserMapper userMapper;

    @Test
    void executeAgentRouteShouldReturnAgentResponse() throws Exception {
        AgentResponse response = agentResponse("task_001", "ok");
        when(agentApplicationService.execute(any(AgentExecuteRequest.class))).thenReturn(response);

        AgentExecuteRequest request = new AgentExecuteRequest();
        request.setUserId(1L);
        request.setSessionId("session_001");
        request.setInputType("TEXT");
        request.setText("today schedule");
        request.setTimezone("Asia/Shanghai");
        request.setCurrentTime("2026-05-30 10:00:00");

        mockMvc.perform(post("/api/agent/execute")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.taskId").value("task_001"))
                .andExpect(jsonPath("$.data.replyText").value("ok"));

        verify(agentApplicationService).execute(any(AgentExecuteRequest.class));
    }

    @Test
    void confirmAgentRouteShouldReturnAgentResponse() throws Exception {
        AgentResponse response = agentResponse("task_001", "confirmed");
        when(agentApplicationService.confirm(any(AgentConfirmRequest.class))).thenReturn(response);

        AgentConfirmRequest request = new AgentConfirmRequest();
        request.setUserId(1L);
        request.setSessionId("session_001");
        request.setConfirmToken("ct_001");

        mockMvc.perform(post("/api/agent/confirm")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskId").value("task_001"))
                .andExpect(jsonPath("$.data.replyText").value("confirmed"));

        verify(agentApplicationService).confirm(any(AgentConfirmRequest.class));
    }

    @Test
    void cancelAgentRouteShouldReturnAgentResponse() throws Exception {
        AgentResponse response = agentResponse("task_001", "canceled");
        when(agentApplicationService.cancel(any(AgentCancelRequest.class))).thenReturn(response);

        AgentCancelRequest request = new AgentCancelRequest();
        request.setUserId(1L);
        request.setSessionId("session_001");
        request.setConfirmToken("ct_001");

        mockMvc.perform(post("/api/agent/cancel")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskId").value("task_001"))
                .andExpect(jsonPath("$.data.replyText").value("canceled"));

        verify(agentApplicationService).cancel(any(AgentCancelRequest.class));
    }

    @Test
    void selectSlotRouteShouldReturnAgentResponse() throws Exception {
        AgentResponse response = agentResponse("task_001", "selected");
        when(agentApplicationService.selectSlot(any(AgentSelectSlotRequest.class))).thenReturn(response);

        AgentSelectSlotRequest request = new AgentSelectSlotRequest();
        request.setUserId(1L);
        request.setSessionId("session_001");
        request.setConfirmToken("ct_001");
        request.setSlotIndex(0);

        mockMvc.perform(post("/api/agent/select-slot")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskId").value("task_001"))
                .andExpect(jsonPath("$.data.replyText").value("selected"));

        verify(agentApplicationService).selectSlot(any(AgentSelectSlotRequest.class));
    }

    @Test
    void selectEventRouteShouldReturnAgentResponse() throws Exception {
        AgentResponse response = agentResponse("task_001", "event selected");
        when(agentApplicationService.selectEvent(any(AgentSelectEventRequest.class))).thenReturn(response);

        AgentSelectEventRequest request = new AgentSelectEventRequest();
        request.setUserId(1L);
        request.setSessionId("session_001");
        request.setConfirmToken("ct_001");
        request.setCandidateIndex(0);

        mockMvc.perform(post("/api/agent/select-event")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.replyText").value("event selected"));

        verify(agentApplicationService).selectEvent(any(AgentSelectEventRequest.class));
    }

    @Test
    void contactListRouteShouldReturnContacts() throws Exception {
        ContactEntity contact = new ContactEntity();
        contact.setId(1L);
        contact.setUserId(1L);
        contact.setName("张三");
        contact.setPhone("13800000001");
        when(contactService.list(1L, null)).thenReturn(Collections.singletonList(contact));

        mockMvc.perform(get("/api/contacts")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("张三"))
                .andExpect(jsonPath("$.data[0].phone").value("13800000001"));
    }

    @Test
    void preferenceRouteShouldReturnDefaults() throws Exception {
        UserPreferenceEntity preference = new UserPreferenceEntity();
        preference.setUserId(1L);
        preference.setDefaultDurationMinutes(60);
        preference.setDefaultReminderMinutes(10);
        when(userPreferenceService.get(1L)).thenReturn(preference);

        mockMvc.perform(get("/api/preferences")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.defaultDurationMinutes").value(60))
                .andExpect(jsonPath("$.data.defaultReminderMinutes").value(10));
    }

    @Test
    void workflowStepsRouteShouldReturnSteps() throws Exception {
        ExecutionLogVO step = new ExecutionLogVO();
        step.setTaskId("task_001");
        step.setStepOrder(1);
        step.setSkillId("router.route");
        step.setStepName("Router");
        step.setStatus("SUCCESS");
        step.setLatencyMs(12L);
        when(commandWorkflowService.listSteps("task_001", 1L)).thenReturn(Collections.singletonList(step));

        mockMvc.perform(get("/api/agent/tasks/{taskId}/steps", "task_001")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].taskId").value("task_001"))
                .andExpect(jsonPath("$.data[0].skillId").value("router.route"))
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"));

        verify(commandWorkflowService).listSteps("task_001", 1L);
    }

    @Test
    void queryCalendarEventsRouteShouldBindQueryParams() throws Exception {
        CalendarEventVO event = calendarEvent();
        when(calendarService.queryEvents(any(QueryEventRequest.class)))
                .thenReturn(Collections.singletonList(event));

        mockMvc.perform(get("/api/calendar/events")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L)
                        .param("userId", "1")
                        .param("startTime", "2026-05-30 00:00:00")
                        .param("endTime", "2026-05-31 00:00:00")
                        .param("keyword", "meeting")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(100))
                .andExpect(jsonPath("$.data[0].title").value("meeting"));

        ArgumentCaptor<QueryEventRequest> captor = ArgumentCaptor.forClass(QueryEventRequest.class);
        verify(calendarService).queryEvents(captor.capture());
        QueryEventRequest request = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(1L, request.getUserId());
        org.junit.jupiter.api.Assertions.assertEquals("meeting", request.getKeyword());
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", request.getStatus());
    }

    @Test
    void createCalendarEventRouteShouldReturnEvent() throws Exception {
        CalendarEventVO event = calendarEvent();
        when(calendarService.createEvent(any(CreateEventRequest.class))).thenReturn(event);

        String body = "{"
                + "\"userId\":1,"
                + "\"title\":\"meeting\","
                + "\"startTime\":\"2026-05-30 10:00:00\","
                + "\"endTime\":\"2026-05-30 11:00:00\""
                + "}";

        mockMvc.perform(post("/api/calendar/events")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.title").value("meeting"))
                .andExpect(jsonPath("$.data.startTime").value("2026-05-30 10:00:00"));

        verify(calendarService).createEvent(any(CreateEventRequest.class));
    }

    @Test
    void deleteCalendarEventRouteShouldReturnBoolean() throws Exception {
        when(calendarService.deleteEvent(eq(100L), eq(1L), eq("SINGLE"))).thenReturn(true);

        mockMvc.perform(delete("/api/calendar/events/{eventId}", 100L)
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L)
                        .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));

        verify(calendarService).deleteEvent(100L, 1L, "SINGLE");
    }

    @Test
    void reminderListRouteShouldReturnJobs() throws Exception {
        ReminderJobVO reminder = new ReminderJobVO();
        reminder.setId(10L);
        reminder.setUserId(1L);
        reminder.setEventId(100L);
        reminder.setJobType("IN_APP");
        reminder.setStatus("PENDING");
        when(reminderJobService.listByUser(1L)).thenReturn(Collections.singletonList(reminder));

        mockMvc.perform(get("/api/reminders")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].eventId").value(100))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));

        verify(reminderJobService).listByUser(1L);
    }

    @Test
    void skillListRouteShouldReturnRegisteredSkills() throws Exception {
        SkillDefinition skill = new SkillDefinition();
        skill.setSkillId("meeting.create");
        skill.setName("Create online meeting");
        skill.setDescription("Create a meeting link");
        when(skillRegistry.list()).thenReturn(Collections.singletonList(skill));

        mockMvc.perform(get("/api/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].skillId").value("meeting.create"));

        verify(skillRegistry).list();
    }

    private AgentResponse agentResponse(String taskId, String replyText) {
        AgentResponse response = new AgentResponse();
        response.setSuccess(true);
        response.setTaskId(taskId);
        response.setNeedConfirm(false);
        response.setNeedClarify(false);
        response.setReplyText(replyText);
        response.setSpeakText(replyText);
        return response;
    }

    private CalendarEventVO calendarEvent() {
        CalendarEventVO event = new CalendarEventVO();
        event.setId(100L);
        event.setUserId(1L);
        event.setTitle("meeting");
        event.setStartTime(LocalDateTime.of(2026, 5, 30, 10, 0, 0));
        event.setEndTime(LocalDateTime.of(2026, 5, 30, 11, 0, 0));
        event.setStatus("ACTIVE");
        return event;
    }
}
