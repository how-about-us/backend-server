package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.config.GooglePlacesProperties;
import com.howaboutus.backend.places.service.dto.GoogleTextSearchResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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

class GooglePlaceSearchClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private GooglePlaceSearchClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GooglePlaceSearchClient(
                builder.build(),
                new GooglePlacesProperties(
                        "test-key",
                        "https://places.googleapis.com",
                        "places.id,places.displayName,places.formattedAddress,places.location,places.primaryType,places.rating,places.photos"
                )
        );
    }

    @Test
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

        List<GoogleTextSearchResponse.PlaceItem> result = client.search("seoul cafe");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo("ChIJ123");
        server.verify();
    }
}
