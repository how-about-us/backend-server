package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.integration.google.GooglePlaceDetailClient;
import com.howaboutus.backend.common.integration.google.GooglePlacePhotoClient;
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
    private final GooglePlacePhotoClient googlePlacePhotoClient = mock(GooglePlacePhotoClient.class);
    private final PlaceDetailService placeDetailService = new PlaceDetailService(googlePlaceDetailClient, googlePlacePhotoClient);

    @Test
    @DisplayName("상세 조회 클라이언트 응답을 서비스 결과로 변환하고 사진 URL을 포함한다")
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
                new GooglePlaceDetailResponse.RegularOpeningHours(List.of("월요일: 09:00~18:00")),
                List.of(new GooglePlaceDetailResponse.Photo("places/ChIJ123/photos/a"))
        );
        given(googlePlaceDetailClient.getDetail("ChIJ123")).willReturn(response);
        given(googlePlacePhotoClient.getPhotoUri("places/ChIJ123/photos/a"))
                .willReturn("https://cdn.example.com/a.jpg");

        PlaceDetailResult result = placeDetailService.getDetail("ChIJ123");

        assertThat(result).isEqualTo(
                PlaceDetailResult.from(response, List.of("https://cdn.example.com/a.jpg")));
        then(googlePlaceDetailClient).should().getDetail("ChIJ123");
        then(googlePlacePhotoClient).should().getPhotoUri("places/ChIJ123/photos/a");
    }
}
