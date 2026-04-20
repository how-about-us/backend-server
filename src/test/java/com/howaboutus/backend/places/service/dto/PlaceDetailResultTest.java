package com.howaboutus.backend.places.service.dto;

import com.howaboutus.backend.common.integration.google.dto.GooglePlaceDetailResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceDetailResultTest {

    @Test
    @DisplayName("Google 상세 응답을 장소 상세 결과로 매핑한다")
    void createsDetailResultFromGoogleResponse() {
        GooglePlaceDetailResponse place = new GooglePlaceDetailResponse(
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
                List.of(
                        new GooglePlaceDetailResponse.Photo("places/ChIJ123/photos/a"),
                        new GooglePlaceDetailResponse.Photo("places/ChIJ123/photos/b")
                )
        );

        PlaceDetailResult result = PlaceDetailResult.from(place);

        assertThat(result.googlePlaceId()).isEqualTo("ChIJ123");
        assertThat(result.name()).isEqualTo("Cafe Layered");
        assertThat(result.formattedAddress()).isEqualTo("서울 종로구 ...");
        assertThat(result.location()).isEqualTo(new PlaceDetailResult.Location(37.57, 126.98));
        assertThat(result.primaryType()).isEqualTo("cafe");
        assertThat(result.rating()).isEqualTo(4.5);
        assertThat(result.phoneNumber()).isEqualTo("02-123-4567");
        assertThat(result.websiteUri()).isEqualTo("https://layered.example");
        assertThat(result.googleMapsUri()).isEqualTo("https://maps.google.com/?cid=123");
        assertThat(result.weekdayDescriptions()).containsExactly("월요일: 09:00~18:00");
        assertThat(result.photoNames()).containsExactly(
                "places/ChIJ123/photos/a",
                "places/ChIJ123/photos/b"
        );
    }

    @Test
    @DisplayName("선택 필드가 없어도 빈 리스트와 null로 안전하게 상세 결과를 만든다")
    void createsDetailResultWhenOptionalFieldsAreMissing() {
        GooglePlaceDetailResponse place = new GooglePlaceDetailResponse(
                "places/ChIJ999",
                null,
                "서울 중구 ...",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        PlaceDetailResult result = PlaceDetailResult.from(place);

        assertThat(result.googlePlaceId()).isEqualTo("ChIJ999");
        assertThat(result.name()).isNull();
        assertThat(result.location()).isNull();
        assertThat(result.primaryType()).isNull();
        assertThat(result.rating()).isNull();
        assertThat(result.phoneNumber()).isNull();
        assertThat(result.websiteUri()).isNull();
        assertThat(result.googleMapsUri()).isNull();
        assertThat(result.weekdayDescriptions()).isEmpty();
        assertThat(result.photoNames()).isEmpty();
    }
}
