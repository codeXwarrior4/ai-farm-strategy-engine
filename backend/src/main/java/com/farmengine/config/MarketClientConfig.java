package com.farmengine.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * HTTP client configuration for data.gov.in market price API.
 * Configures connection and read timeouts.
 */
@Configuration
public class MarketClientConfig {

    @Value("${app.market.data-gov-in.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${app.market.data-gov-in.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @Bean("marketRestClient")
    public RestClient marketRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return builder.requestFactory(factory).build();
    }
}
