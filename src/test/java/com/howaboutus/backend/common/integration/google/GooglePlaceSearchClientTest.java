package com.howaboutus.backend.common.integration.google;

import com.howaboutus.backend.common.config.properties.GooglePlacesProperties;
import com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchRequest;
import com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GooglePlaceSearchClientTest {

    private MockRestServiceServer server;
    private GooglePlaceSearchClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://places.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GooglePlaceSearchClient(
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
    @DisplayName("Google Places 텍스트 검색 엔드포인트로 올바른 헤더와 함께 요청을 전송한다")
    void searchesPlacesUsingTextSearchEndpoint() {
        server.expect(requestTo("https://places.googleapis.com/v1/places:searchText"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(header(
                        "X-Goog-FieldMask",
                        "places.id,places.displayName,places.formattedAddress,places.location,places.primaryType,places.rating,places.photos"
                ))
                .andRespond(withSuccess("""
                        {
                          "places": [
                            {
                              "id": "ChIJ123",
                              "displayName": {"text": "Cafe Layered", "languageCode": "ko"},
                              "formattedAddress": "서울 종로구 ...",
                              "location": {"latitude": 37.57, "longitude": 126.98},
                              "primaryType": "cafe",
                              "rating": 4.5,
                              "photos": [{"name": "places/ChIJ123/photos/abc"}]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<GoogleTextSearchResponse.PlaceItem> result = client.search("seoul cafe", 37.5, 127.0, 5000.0);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo("ChIJ123");
        server.verify();
    }

    @Test
    @DisplayName("위치 정보를 넘기면 locationBias.circle이 포함된 요청 본문을 전송한다")
    void searchesWithLocationBiasWhenCoordinatesProvided() {
        server.expect(requestTo("https://places.googleapis.com/v1/places:searchText"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(hasRequestBody(
                        GoogleTextSearchRequest.withKorean("seoul cafe", 37.5, 127.0, 3000.0),
                        GoogleTextSearchRequest.class
                ))
                .andRespond(withSuccess("{\"places\": []}", MediaType.APPLICATION_JSON));

        List<GoogleTextSearchResponse.PlaceItem> result = client.search("seoul cafe", 37.5, 127.0, 3000.0);

        assertThat(result).isEmpty();
        server.verify();
    }

    private <T> RequestMatcher hasRequestBody(T expectedBody, Class<T> bodyType) {
        return request -> assertThat(readBody(request, bodyType)).isEqualTo(expectedBody);
    }

    private <T> T readBody(org.springframework.http.client.ClientHttpRequest request, Class<T> bodyType)
            throws IOException {
        String requestBody = ((MockClientHttpRequest) request).getBodyAsString();
        return objectMapper.readValue(requestBody, bodyType);
    }
}
