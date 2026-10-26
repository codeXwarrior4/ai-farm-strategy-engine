package com.farmengine.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class WeatherClientConfig {

    @Value("${app.weather.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${app.weather.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Bean
    @Primary
    public RestClient weatherRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return builder.requestFactory(factory).build();
    }
}
