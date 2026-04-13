package com.howaboutus.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class GoogleOAuthClientConfig {

    @Bean
    RestClient googleOAuthRestClient() {
        return RestClient.builder().build();
    }
}
