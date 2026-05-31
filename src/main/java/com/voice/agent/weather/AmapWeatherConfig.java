package com.voice.agent.weather;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 高德天气 HTTP Client 配置。
 */
@Configuration
public class AmapWeatherConfig {
    @Bean("amapRestTemplate")
    public RestTemplate amapRestTemplate(RestTemplateBuilder builder, AmapProperties properties) {
        return builder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .build();
    }
}
