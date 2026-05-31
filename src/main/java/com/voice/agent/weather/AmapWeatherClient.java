package com.voice.agent.weather;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 高德实时天气 Client。
 *
 * <p>天气接口要求使用 adcode，因此查询流程为：城市名称 -> 行政区编码 -> 实时天气。</p>
 */
@Component
public class AmapWeatherClient {
    private final RestTemplate restTemplate;
    private final AmapProperties properties;

    public AmapWeatherClient(
            @Qualifier("amapRestTemplate") RestTemplate restTemplate,
            AmapProperties properties
    ) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public Map<String, Object> queryLiveWeather(String location) {
        if (!StringUtils.hasText(location)) {
            throw new IllegalArgumentException("天气查询地点不能为空");
        }
        requireConfiguredKey();

        Map<String, Object> district = resolveDistrict(location.trim());
        String adcode = text(district.get("adcode"));
        if (!StringUtils.hasText(adcode)) {
            throw new IllegalStateException("无法获取城市行政区编码：" + location);
        }

        Map<String, Object> live = queryWeather(adcode);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("location", firstText(live.get("city"), district.get("name"), location.trim()));
        result.put("adcode", adcode);
        result.put("province", text(live.get("province")));
        result.put("condition", text(live.get("weather")));
        result.put("temperature_celsius", text(live.get("temperature")));
        result.put("humidity", text(live.get("humidity")));
        result.put("wind_direction", text(live.get("winddirection")));
        result.put("wind_power", text(live.get("windpower")));
        result.put("report_time", text(live.get("reporttime")));
        result.put("provider", "amap");
        return result;
    }

    private Map<String, Object> resolveDistrict(String location) {
        URI uri = UriComponentsBuilder.fromHttpUrl(normalizeBaseUrl())
                .path("/config/district")
                .queryParam("keywords", location)
                .queryParam("subdistrict", 0)
                .queryParam("extensions", "base")
                .queryParam("key", properties.getWebServiceKey())
                .build()
                .encode()
                .toUri();
        Map<String, Object> response = get(uri);
        validateResponse(response, "高德行政区域查询");
        return firstObject(response, "districts", "未找到城市：" + location);
    }

    private Map<String, Object> queryWeather(String adcode) {
        URI uri = UriComponentsBuilder.fromHttpUrl(normalizeBaseUrl())
                .path("/weather/weatherInfo")
                .queryParam("city", adcode)
                .queryParam("extensions", "base")
                .queryParam("output", "JSON")
                .queryParam("key", properties.getWebServiceKey())
                .build()
                .encode()
                .toUri();
        Map<String, Object> response = get(uri);
        validateResponse(response, "高德天气查询");
        return firstObject(response, "lives", "未查询到实时天气");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(URI uri) {
        try {
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, null, Map.class);
            return response.getBody() == null ? Collections.emptyMap() : response.getBody();
        } catch (RestClientException e) {
            // 不将原始异常继续暴露给用户，避免 URL 中的 Web 服务 Key 被输出到页面或日志。
            throw new IllegalStateException("高德天气服务请求失败，请稍后重试");
        }
    }

    private void validateResponse(Map<String, Object> response, String operation) {
        if (!"1".equals(text(response.get("status")))) {
            String info = text(response.get("info"));
            throw new IllegalStateException(operation + "失败" + (StringUtils.hasText(info) ? "：" + info : ""));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstObject(Map<String, Object> response, String field, String emptyMessage) {
        Object value = response.get(field);
        if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
            throw new IllegalStateException(emptyMessage);
        }
        Object first = ((List<?>) value).get(0);
        if (!(first instanceof Map)) {
            throw new IllegalStateException(emptyMessage);
        }
        return (Map<String, Object>) first;
    }

    private void requireConfiguredKey() {
        if (!StringUtils.hasText(properties.getWebServiceKey())) {
            throw new IllegalStateException("高德 Web 服务 Key 未配置");
        }
    }

    private String normalizeBaseUrl() {
        String baseUrl = properties.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return "https://restapi.amap.com/v3";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
