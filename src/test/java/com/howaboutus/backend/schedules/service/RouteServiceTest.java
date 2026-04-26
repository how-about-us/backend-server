package com.howaboutus.backend.schedules.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.common.integration.google.GoogleRoutesClient;
import com.howaboutus.backend.common.integration.google.dto.GoogleComputeRoutesResponse;
import com.howaboutus.backend.schedules.service.dto.RouteResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    @Mock
    private GoogleRoutesClient googleRoutesClient;

    private RouteService routeService;

    @BeforeEach
    void setUp() {
        routeService = new RouteService(googleRoutesClient);
    }

    @Test
    @DisplayName("이동 수단을 정규화한 값으로 Google Routes를 호출하고 결과에도 반환한다")
    void computeRouteNormalizesTravelMode() {
        given(googleRoutesClient.computeRoutes("origin", "destination", "WALKING"))
                .willReturn(responseWithRoute(1200, "900s"));

        RouteResult result = routeService.computeRoute("origin", "destination", " walking ");

        assertThat(result.distanceMeters()).isEqualTo(1200);
        assertThat(result.durationSeconds()).isEqualTo(900);
        assertThat(result.travelMode()).isEqualTo("WALKING");
    }

    @Test
    @DisplayName("이동 수단이 없으면 DRIVING 기본값으로 Google Routes를 호출한다")
    void computeRouteUsesDefaultTravelMode() {
        given(googleRoutesClient.computeRoutes("origin", "destination", "DRIVING"))
                .willReturn(responseWithRoute(1200, "900s"));

        RouteResult result = routeService.computeRoute("origin", "destination", null);

        assertThat(result.travelMode()).isEqualTo("DRIVING");
    }

    @Test
    @DisplayName("허용되지 않는 이동 수단이면 INVALID_TRAVEL_MODE 예외를 던진다")
    void computeRouteRejectsInvalidTravelMode() {
        assertThatThrownBy(() -> routeService.computeRoute("origin", "destination", "FLYING"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TRAVEL_MODE);
    }

    @Test
    @DisplayName("Google Routes 응답이 null이면 EXTERNAL_API_ERROR 예외를 던진다")
    void computeRouteThrowsWhenResponseIsNull() {
        given(googleRoutesClient.computeRoutes("origin", "destination", "DRIVING")).willReturn(null);

        assertThatThrownBy(() -> routeService.computeRoute("origin", "destination", "DRIVING"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    @DisplayName("Google Routes 응답의 routes가 비어 있으면 EXTERNAL_API_ERROR 예외를 던진다")
    void computeRouteThrowsWhenRoutesAreEmpty() {
        given(googleRoutesClient.computeRoutes("origin", "destination", "DRIVING"))
                .willReturn(new GoogleComputeRoutesResponse(List.of()));

        assertThatThrownBy(() -> routeService.computeRoute("origin", "destination", "DRIVING"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    private GoogleComputeRoutesResponse responseWithRoute(Integer distanceMeters, String duration) {
        return new GoogleComputeRoutesResponse(
                List.of(new GoogleComputeRoutesResponse.Route(distanceMeters, duration))
        );
    }
}
