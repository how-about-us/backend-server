package com.howaboutus.backend.common.integration.google;

import com.howaboutus.backend.common.config.properties.GooglePlacesProperties;
import com.howaboutus.backend.common.integration.google.dto.GooglePlaceDetailResponse;
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
                        "id,displayName,formattedAddress,location,primaryType,rating,nationalPhoneNumber,websiteUri,googleMapsUri,regularOpeningHours,photos.name,reviews,reviewSummary.text"
                )
        );
    }

    @Test
    @DisplayName("Google Places 상세 조회 엔드포인트로 올바른 헤더와 함께 요청한다")
    void getsPlaceDetailUsingPlaceDetailsEndpoint() {
        server.expect(requestTo("https://places.googleapis.com/v1/places/ChIJ123?languageCode=ko"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(header(
                        "X-Goog-FieldMask",
                        "id,displayName,formattedAddress,location,primaryType,rating,nationalPhoneNumber,websiteUri,googleMapsUri,regularOpeningHours,photos.name,reviews,reviewSummary.text"
                ))
                .andExpect(hasEmptyRequestBody())
                .andRespond(withSuccess("""
                        {
                          "id": "places/ChIJ123",
                          "displayName": {"text": "Cafe Layered", "languageCode": "ko"},
                          "regularOpeningHours": {
                            "openNow": true,
                            "periods": [
                              {
                                "open": {"day": 1, "hour": 9, "minute": 0},
                                "close": {"day": 1, "hour": 18, "minute": 0}
                              }
                            ],
                            "weekdayDescriptions": ["월요일: 09:00~18:00"],
                            "nextOpenTime": "2026-04-30T00:00:00Z",
                            "nextCloseTime": "2026-04-29T09:00:00Z"
                          },
                          "reviewSummary": {
                            "text": {"text": "디저트와 분위기가 좋아요", "languageCode": "ko"}
                          },
                          "reviews": [
                            {
                              "name": "places/ChIJ123/reviews/1",
                              "relativePublishTimeDescription": "2주 전",
                              "rating": 5,
                              "text": {"text": "케이크가 맛있어요", "languageCode": "ko"},
                              "authorAttribution": {"displayName": "홍길동"},
                              "publishTime": "2026-04-01T12:34:56Z"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        GooglePlaceDetailResponse result = client.getDetail("ChIJ123");

        assertThat(result.id()).isEqualTo("places/ChIJ123");
        assertThat(result.displayName().text()).isEqualTo("Cafe Layered");
        assertThat(result.regularOpeningHours().openNow()).isTrue();
        assertThat(result.regularOpeningHours().periods().getFirst().open().hour()).isEqualTo(9);
        assertThat(result.regularOpeningHours().weekdayDescriptions()).containsExactly("월요일: 09:00~18:00");
        assertThat(result.reviewSummary().text().text()).isEqualTo("디저트와 분위기가 좋아요");
        assertThat(result.reviews().getFirst().rating()).isEqualTo(5.0);
        assertThat(result.reviews().getFirst().text().text()).isEqualTo("케이크가 맛있어요");
        assertThat(result.reviews().getFirst().authorAttribution().displayName()).isEqualTo("홍길동");
        assertThat(result.reviews().getFirst().publishTime()).isEqualTo("2026-04-01T12:34:56Z");
        server.verify();
    }

    private RequestMatcher hasEmptyRequestBody() {
        return request -> assertThat(((MockClientHttpRequest) request).getBodyAsString()).isEmpty();
    }
}
