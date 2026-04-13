package com.howaboutus.backend.common.integration.google;

import com.howaboutus.backend.common.config.GooglePlacesProperties;
import com.howaboutus.backend.common.integration.google.dto.GooglePlaceDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GooglePlaceDetailClientTest {

    private MockRestServiceServer server;
    private GooglePlaceDetailClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://places.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GooglePlaceDetailClient(
                builder.build(),
                new GooglePlacesProperties(
                        "test-key",
                        "https://places.googleapis.com/",
                        "places.id,places.displayName,places.formattedAddress,places.location,places.primaryType,places.rating,places.photos",
                        "id,displayName,formattedAddress,location,primaryType,rating,nationalPhoneNumber,websiteUri,googleMapsUri,regularOpeningHours.weekdayDescriptions,photos.name"
                )
        );
    }

    @Test
    @DisplayName("Google Places 상세 조회 엔드포인트로 올바른 헤더와 함께 요청한다")
    void getsPlaceDetailUsingPlaceDetailsEndpoint() {
        server.expect(requestTo("https://places.googleapis.com/v1/places/ChIJ123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(header(
                        "X-Goog-FieldMask",
                        "id,displayName,formattedAddress,location,primaryType,rating,nationalPhoneNumber,websiteUri,googleMapsUri,regularOpeningHours.weekdayDescriptions,photos.name"
                ))
                .andRespond(withSuccess("""
                        {
                          "id": "places/ChIJ123",
                          "displayName": {"text": "Cafe Layered", "languageCode": "ko"}
                        }
                        """, MediaType.APPLICATION_JSON));

        GooglePlaceDetailResponse result = client.getDetail("ChIJ123");

        assertThat(result.id()).isEqualTo("places/ChIJ123");
        assertThat(result.displayName().text()).isEqualTo("Cafe Layered");
        server.verify();
    }
}
