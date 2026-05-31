package com.voice.agent;

import com.voice.agent.weather.AmapProperties;
import com.voice.agent.weather.AmapWeatherClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AmapWeatherClientTest {
    @Test
    void queryLiveWeatherShouldResolveAdcodeAndMapLiveWeather() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        AmapProperties properties = new AmapProperties();
        properties.setBaseUrl("https://restapi.amap.com/v3");
        properties.setWebServiceKey("local-test-key");
        AmapWeatherClient client = new AmapWeatherClient(restTemplate, properties);

        server.expect(once(), request -> {
                    java.net.URI uri = request.getURI();
                    assertEquals("/v3/config/district", uri.getPath());
                    assertEquals("keywords=%E4%B8%8A%E6%B5%B7&subdistrict=0&extensions=base&key=local-test-key", uri.getRawQuery());
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"status\":\"1\",\"districts\":[{\"name\":\"上海市\",\"adcode\":\"310000\"}]}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(once(), request -> {
                    java.net.URI uri = request.getURI();
                    assertEquals("/v3/weather/weatherInfo", uri.getPath());
                    assertEquals("city=310000&extensions=base&output=JSON&key=local-test-key", uri.getRawQuery());
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"status\":\"1\",\"lives\":[{"
                                + "\"province\":\"上海\",\"city\":\"上海市\",\"adcode\":\"310000\","
                                + "\"weather\":\"多云\",\"temperature\":\"27\","
                                + "\"winddirection\":\"东南\",\"windpower\":\"≤3\","
                                + "\"humidity\":\"65\",\"reporttime\":\"2026-05-31 16:00:00\"}]}",
                        MediaType.APPLICATION_JSON
                ));

        Map<String, Object> result = client.queryLiveWeather("上海");

        assertEquals("上海市", result.get("location"));
        assertEquals("多云", result.get("condition"));
        assertEquals("27", result.get("temperature_celsius"));
        assertEquals("amap", result.get("provider"));
        server.verify();
    }
}
