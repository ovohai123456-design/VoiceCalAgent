package com.voice.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@Slf4j
public class EmailDeliveryService {
    private final JavaMailSender mailSender;

    @Value("${voicecal.email.enabled:false}")
    private boolean enabled;

    @Value("${voicecal.email.from:${spring.mail.username:}}")
    private String from;

    public EmailDeliveryService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void send(Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Email payload cannot be null");
        }
        send(
                asText(payload.get("receiver")),
                asText(payload.get("subject")),
                asText(payload.get("content"))
        );
    }

    public void send(String receiver, String subject, String content) {
        if (!enabled) {
            throw new IllegalStateException("SMTP email delivery is disabled");
        }
        if (!StringUtils.hasText(receiver)) {
            throw new IllegalArgumentException("Email receiver cannot be empty");
        }
        if (!StringUtils.hasText(from)) {
            throw new IllegalStateException("Email sender is not configured");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from.trim());
        message.setTo(receiver.trim());
        message.setSubject(StringUtils.hasText(subject) ? subject.trim() : "VoiceCal 日程提醒");
        message.setText(content == null ? "" : content);
        mailSender.send(message);
        log.info("SMTP email sent receiver={} subject={}", receiver, message.getSubject());
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
