package com.howaboutus.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.howaboutus.backend.common.config.properties")
public class HowAboutUsBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(HowAboutUsBackendApplication.class, args);
    }

}
