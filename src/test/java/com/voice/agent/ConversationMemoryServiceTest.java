package com.voice.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voice.agent.agent.ConversationMemoryService;
import com.voice.agent.mapper.ConversationMessageMapper;
import com.voice.agent.mapper.ConversationSessionContextMapper;
import com.voice.agent.mapper.ConversationStateMapper;
import com.voice.agent.model.entity.ConversationSessionContextEntity;
import com.voice.agent.model.vo.CalendarEventVO;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationMemoryServiceTest {
    private final ConversationMessageMapper messageMapper = mock(ConversationMessageMapper.class);
    private final ConversationStateMapper stateMapper = mock(ConversationStateMapper.class);
    private final ConversationSessionContextMapper sessionContextMapper = mock(ConversationSessionContextMapper.class);
    private final ConversationMemoryService service = new ConversationMemoryService(
            messageMapper,
            stateMapper,
            sessionContextMapper,
            new ObjectMapper()
    );

    @Test
    void shouldRememberAndResolveRecentQueryEventsByDisplayedOrder() {
        ConversationSessionContextEntity context = new ConversationSessionContextEntity();
        context.setId(10L);
        context.setUserId(1L);
        context.setSessionId("session_001");
        when(sessionContextMapper.selectOne(any())).thenReturn(context);

        service.rememberRecentQueryEvents(1L, "session_001", Arrays.asList(event(88L), event(99L)));

        assertEquals("[88,99]", context.getLastQueryEventIdsJson());
        assertEquals(Long.valueOf(88L), service.findRecentQueryEventId(1L, "session_001", 0));
        assertEquals(Long.valueOf(99L), service.findRecentQueryEventId(1L, "session_001", 1));
        verify(sessionContextMapper).updateById(context);
    }

    private CalendarEventVO event(Long id) {
        CalendarEventVO event = new CalendarEventVO();
        event.setId(id);
        return event;
    }
}
