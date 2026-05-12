package com.example.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${inventory.service.url}")
    private String inventoryServiceUrl;

    @Bean
    public WebClient inventoryWebClient() {
        // Set TCP-level connect timeout (separate from Resilience4j TimeLimiter)
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(300)); // safety net at HTTP layer

        return WebClient.builder()
                .baseUrl(inventoryServiceUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
