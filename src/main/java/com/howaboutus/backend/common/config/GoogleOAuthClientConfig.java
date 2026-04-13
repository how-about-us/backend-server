package com.howaboutus.backend.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GoogleOAuthProperties.class)
public class GoogleOAuthClientConfig {

    @Bean
    RestClient googleOAuthRestClient() {
        return RestClient.builder().build();
    }
}
