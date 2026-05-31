package com.voice.agent;

import com.voice.agent.service.EmailDeliveryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailDeliveryServiceTest {
    @Test
    void shouldSendSimpleSmtpEmail() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailDeliveryService service = new EmailDeliveryService(mailSender);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "from", "sender@example.com");

        service.send("receiver@example.com", "subject", "content");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertEquals("sender@example.com", message.getFrom());
        assertEquals("receiver@example.com", message.getTo()[0]);
        assertEquals("subject", message.getSubject());
        assertEquals("content", message.getText());
    }
}
