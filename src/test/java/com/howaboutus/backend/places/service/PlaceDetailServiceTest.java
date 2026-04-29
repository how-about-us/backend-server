package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.integration.google.GooglePlaceDetailClient;
import com.howaboutus.backend.common.integration.google.dto.GooglePlaceDetailResponse;
import com.howaboutus.backend.places.service.dto.PlaceDetailResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class PlaceDetailServiceTest {

    private final GooglePlaceDetailClient googlePlaceDetailClient = mock(GooglePlaceDetailClient.class);
    private final PlaceDetailService placeDetailService = new PlaceDetailService(googlePlaceDetailClient);

    @Test
    @DisplayName("상세 조회 클라이언트 응답을 서비스 결과로 변환한다")
    void returnsMappedPlaceDetail() {
        GooglePlaceDetailResponse response = new GooglePlaceDetailResponse(
                "places/ChIJ123",
                new GooglePlaceDetailResponse.DisplayName("Cafe Layered", "ko"),
                "서울 종로구 ...",
                new GooglePlaceDetailResponse.Location(37.57, 126.98),
                "cafe",
                4.5,
                "02-123-4567",
                "https://layered.example",
                "https://maps.google.com/?cid=123",
                new GooglePlaceDetailResponse.RegularOpeningHours(
                        null,
                        null,
                        null,
                        null,
                        List.of("월요일: 09:00~18:00"),
                        null,
                        null
                ),
                List.of(new GooglePlaceDetailResponse.Photo("places/ChIJ123/photos/a")),
                null,
                null
        );
        given(googlePlaceDetailClient.getDetail("ChIJ123")).willReturn(response);

        PlaceDetailResult result = placeDetailService.getDetail("ChIJ123");

        assertThat(result).isEqualTo(PlaceDetailResult.from(response));
        then(googlePlaceDetailClient).should().getDetail("ChIJ123");
    }
}
