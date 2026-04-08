package com.howaboutus.backend.common.client.google.routes;

import com.howaboutus.backend.common.client.google.support.TestRestClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleRoutesClientTest {

    @Test
    void clientDoesNotDeclareNestedApiDtos() {
        assertThat(GoogleRoutesClient.class.getDeclaredClasses()).isEmpty();
    }

    @Test
    void computeRouteSendsFieldMaskAndMapsResponse() {
        TestRestClientFactory.RestClientFixture fixture =
                TestRestClientFactory.create("https://routes.googleapis.com");
        GoogleRoutesClient client = new GoogleRoutesClient(fixture.restClient(), "test-api-key");

        fixture.server().expect(requestTo("https://routes.googleapis.com/directions/v2:computeRoutes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-api-key"))
                .andExpect(header("X-Goog-FieldMask",
                        "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline"))
                .andExpect(content().json("""
                        {
                          "origin": {
                            "location": {
                              "latLng": {
                                "latitude": 37.5546,
                                "longitude": 126.9706
                              }
                            }
                          },
                          "destination": {
                            "location": {
                              "latLng": {
                                "latitude": 37.5665,
                                "longitude": 126.978
                              }
                            }
                          },
                          "travelMode": "TRANSIT",
                          "languageCode": "ko"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "routes": [
                            {
                              "distanceMeters": 1200,
                              "duration": "600s",
                              "polyline": {
                                "encodedPolyline": "abcd"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = client.computeRoute(new GoogleComputeRouteRequest(
                37.5546,
                126.9706,
                37.5665,
                126.9780,
                "TRANSIT",
                "ko"
        ));

        assertThat(result.distanceMeters()).isEqualTo(1200);
        assertThat(result.duration()).isEqualTo("600s");
        assertThat(result.encodedPolyline()).isEqualTo("abcd");
        assertThat(result.travelMode()).isEqualTo("TRANSIT");

        fixture.server().verify();
    }
}
