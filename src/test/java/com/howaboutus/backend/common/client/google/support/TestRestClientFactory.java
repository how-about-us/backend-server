package com.howaboutus.backend.common.client.google.support;

import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

public final class TestRestClientFactory {

    private TestRestClientFactory() {
    }

    public static RestClientFixture create(String baseUrl) {
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        return new RestClientFixture(builder.build(), server);
    }

    public record RestClientFixture(RestClient restClient, MockRestServiceServer server) {
    }
}
