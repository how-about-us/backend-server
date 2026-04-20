package com.howaboutus.backend.common.integration.google;

import com.howaboutus.backend.common.config.properties.GooglePlacesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GooglePlacePhotoClientTest {

    private MockRestServiceServer server;
    private GooglePlacePhotoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://places.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GooglePlacePhotoClient(
                builder.build(),
                new GooglePlacesProperties(
                        "test-key",
                        "https://places.googleapis.com/",
                        "places.id,places.displayName",
                        "id,displayName"
                )
        );
    }

    @Test
    @DisplayName("Photo Media API로 photoName에 해당하는 photoUri를 반환한다")
    void returnsPhotoUriForGivenPhotoName() {
        String photoName = "places/ChIJ123/photos/abc";
        server.expect(requestTo(
                        "https://places.googleapis.com/v1/" + photoName
                                + "/media?maxWidthPx=400&maxHeightPx=400&skipHttpRedirect=true"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(hasEmptyRequestBody())
                .andRespond(withSuccess("""
                        {
                          "name": "places/ChIJ123/photos/abc",
                          "photoUri": "https://lh3.googleusercontent.com/photo.jpg"
                        }
                        """, MediaType.APPLICATION_JSON));

        String result = client.getPhotoUri(photoName);

        assertThat(result).isEqualTo("https://lh3.googleusercontent.com/photo.jpg");
        server.verify();
    }

    private RequestMatcher hasEmptyRequestBody() {
        return request -> assertThat(((MockClientHttpRequest) request).getBodyAsString()).isEmpty();
    }
}
