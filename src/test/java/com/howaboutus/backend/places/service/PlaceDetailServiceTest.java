package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.integration.google.GooglePlaceDetailClient;
import com.howaboutus.backend.common.integration.google.dto.GooglePlaceDetailResponse;
import com.howaboutus.backend.common.integration.google.dto.GooglePlaceDisplayName;
import com.howaboutus.backend.common.integration.google.dto.GooglePlaceLocalizedText;
import com.howaboutus.backend.common.integration.google.dto.GooglePlaceLocation;
import com.howaboutus.backend.common.integration.google.dto.GooglePlacePhoto;
import com.howaboutus.backend.places.service.dto.PlaceDetailResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PlaceDetailServiceTest {

    @Mock
    private GooglePlaceDetailClient googlePlaceDetailClient;
    @InjectMocks
    private PlaceDetailService placeDetailService;

    @Test
    @DisplayName("상세 조회 클라이언트 응답을 서비스 결과로 변환한다")
    void returnsMappedPlaceDetail() {
        GooglePlaceDetailResponse response = new GooglePlaceDetailResponse(
                "places/ChIJ123",
                new GooglePlaceDisplayName("Cafe Layered", "ko"),
                "서울 종로구 ...",
                new GooglePlaceLocation(37.57, 126.98),
                "cafe",
                new GooglePlaceLocalizedText("카페", "ko"),
                4.5,
                128,
                "02-123-4567",
                "https://layered.example",
                "https://maps.google.com/?cid=123",
                new GooglePlaceDetailResponse.RegularOpeningHours(
                        true,
                        null,
                        null,
                        null,
                        List.of("월요일: 09:00~18:00"),
                        null,
                        null
                ),
                List.of(new GooglePlacePhoto("places/ChIJ123/photos/a")),
                null,
                null
        );
        given(googlePlaceDetailClient.getDetail("ChIJ123")).willReturn(response);

        PlaceDetailResult result = placeDetailService.getDetail("ChIJ123");

        assertThat(result).isEqualTo(PlaceDetailResult.from(response));
        assertThat(result.regularOpeningHours().openNow()).isTrue();
        assertThat(result.regularOpeningHours().weekdayDescriptions()).containsExactly("월요일: 09:00~18:00");
        then(googlePlaceDetailClient).should().getDetail("ChIJ123");
    }
}
