package com.howaboutus.backend.common.client.google.places;

import com.howaboutus.backend.common.client.google.exception.GoogleApiClientException;
import com.howaboutus.backend.common.client.google.support.TestRestClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GooglePlacesClientTest {

    @Test
    void clientDoesNotDeclareNestedApiDtos() {
        assertThat(GooglePlacesClient.class.getDeclaredClasses()).isEmpty();
    }

    @Test
    void searchTextSendsFieldMaskAndMapsResponse() {
        TestRestClientFactory.RestClientFixture fixture =
                TestRestClientFactory.create("https://places.googleapis.com");
        GooglePlacesClient client = new GooglePlacesClient(fixture.restClient(), "test-api-key");

        fixture.server().expect(requestTo("https://places.googleapis.com/v1/places:searchText"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-api-key"))
                .andExpect(header("X-Goog-FieldMask",
                        "places.id,places.displayName,places.formattedAddress,places.location,places.rating,places.photos.name"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json("""
                        {
                          "textQuery": "Seoul station",
                          "languageCode": "ko",
                          "regionCode": "KR",
                          "maxResultCount": 5
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "places": [
                            {
                              "id": "ChIJz-VmoyqufDUR64Pq5LTtioU",
                              "displayName": { "text": "Seoul Station" },
                              "formattedAddress": "405 Hangang-daero, Seoul",
                              "location": {
                                "latitude": 37.5546,
                                "longitude": 126.9706
                              },
                              "rating": 4.2,
                              "photos": [
                                { "name": "places/ChIJz-VmoyqufDUR64Pq5LTtioU/photos/photo-1" }
                              ]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = client.searchText(new GooglePlacesSearchRequest("Seoul station", "ko", "KR", 5));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().googlePlaceId()).isEqualTo("ChIJz-VmoyqufDUR64Pq5LTtioU");
        assertThat(result.getFirst().displayName()).isEqualTo("Seoul Station");
        assertThat(result.getFirst().formattedAddress()).isEqualTo("405 Hangang-daero, Seoul");
        assertThat(result.getFirst().latitude()).isEqualTo(37.5546);
        assertThat(result.getFirst().longitude()).isEqualTo(126.9706);
        assertThat(result.getFirst().rating()).isEqualTo(4.2);
        assertThat(result.getFirst().photoNames())
                .containsExactly("places/ChIJz-VmoyqufDUR64Pq5LTtioU/photos/photo-1");

        fixture.server().verify();
    }

    @Test
    void getPlaceDetailsSendsFieldMaskAndMapsResponse() {
        TestRestClientFactory.RestClientFixture fixture =
                TestRestClientFactory.create("https://places.googleapis.com");
        GooglePlacesClient client = new GooglePlacesClient(fixture.restClient(), "test-api-key");

        fixture.server().expect(requestTo("https://places.googleapis.com/v1/places/ChIJz-VmoyqufDUR64Pq5LTtioU"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Goog-Api-Key", "test-api-key"))
                .andExpect(header("X-Goog-FieldMask",
                        "id,displayName,formattedAddress,location,rating,photos.name"))
                .andRespond(withSuccess("""
                        {
                          "id": "ChIJz-VmoyqufDUR64Pq5LTtioU",
                          "displayName": { "text": "Seoul Station" },
                          "formattedAddress": "405 Hangang-daero, Seoul",
                          "location": {
                            "latitude": 37.5546,
                            "longitude": 126.9706
                          },
                          "rating": 4.2,
                          "photos": [
                            { "name": "places/ChIJz-VmoyqufDUR64Pq5LTtioU/photos/photo-1" }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = client.getPlaceDetails("ChIJz-VmoyqufDUR64Pq5LTtioU");

        assertThat(result.googlePlaceId()).isEqualTo("ChIJz-VmoyqufDUR64Pq5LTtioU");
        assertThat(result.displayName()).isEqualTo("Seoul Station");
        assertThat(result.formattedAddress()).isEqualTo("405 Hangang-daero, Seoul");
        assertThat(result.latitude()).isEqualTo(37.5546);
        assertThat(result.longitude()).isEqualTo(126.9706);
        assertThat(result.rating()).isEqualTo(4.2);
        assertThat(result.photoNames()).containsExactly("places/ChIJz-VmoyqufDUR64Pq5LTtioU/photos/photo-1");

        fixture.server().verify();
    }

    @Test
    void searchTextWrapsGoogleErrorAsDomainException() {
        TestRestClientFactory.RestClientFixture fixture =
                TestRestClientFactory.create("https://places.googleapis.com");
        GooglePlacesClient client = new GooglePlacesClient(fixture.restClient(), "test-api-key");

        fixture.server().expect(requestTo("https://places.googleapis.com/v1/places:searchText"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.searchText(new GooglePlacesSearchRequest("Seoul station", "ko", "KR", 5)))
                .isInstanceOf(GoogleApiClientException.class)
                .hasMessageContaining("Google Places API");

        fixture.server().verify();
    }
}
