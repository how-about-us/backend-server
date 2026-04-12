package com.howaboutus.backend.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    private static final PostgreSQLContainer POSTGRES;
    private static final GenericContainer<?> REDIS;
    private static final MongoDBContainer MONGO;

    static {
        POSTGRES = new PostgreSQLContainer(
                DockerImageName.parse("postgis/postgis:17-3.5")
                        .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("howaboutus_test")
                .withUsername("test")
                .withPassword("test");

        //noinspection resource
        REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
                .withExposedPorts(6379);

        MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8"));

        POSTGRES.start();
        REDIS.start();
        MONGO.start();
    }

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
    }
}
