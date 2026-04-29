package com.howaboutus.backend.places.service.dto;

import com.howaboutus.backend.common.integration.google.dto.GooglePlaceDetailResponse;
import com.howaboutus.backend.common.integration.google.dto.GooglePlaceAuthorAttribution;
import com.howaboutus.backend.common.integration.google.dto.GooglePlaceDisplayName;
import com.howaboutus.backend.common.integration.google.dto.GooglePlaceLocalizedText;
import com.howaboutus.backend.common.integration.google.dto.GooglePlaceLocation;
import com.howaboutus.backend.common.integration.google.dto.GooglePlacePhoto;
import com.howaboutus.backend.common.integration.google.dto.GooglePlaceReviewSummary;
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
                        "DRIVE_THROUGH",
                        List.of(new GooglePlaceDetailResponse.SpecialDay(
                                new GooglePlaceDetailResponse.Date(2026, 5, 5)
                        )),
                        List.of(new GooglePlaceDetailResponse.Period(
                                new GooglePlaceDetailResponse.TimePoint(1, 9, 0, null, false),
                                new GooglePlaceDetailResponse.TimePoint(1, 18, 0, null, true)
                        )),
                        List.of("월요일: 09:00~18:00"),
                        "2026-04-30T00:00:00Z",
                        "2026-04-29T09:00:00Z"
                ),
                List.of(
                        new GooglePlacePhoto("places/ChIJ123/photos/a"),
                        new GooglePlacePhoto("places/ChIJ123/photos/b")
                ),
                new GooglePlaceReviewSummary(
                        new GooglePlaceLocalizedText("디저트와 분위기가 좋아요", "ko")
                ),
                List.of(new GooglePlaceDetailResponse.Review(
                        "places/ChIJ123/reviews/1",
                        "2주 전",
                        5.0,
                        new GooglePlaceLocalizedText("케이크가 맛있어요", "ko"),
                        new GooglePlaceLocalizedText("The cake is delicious", "en"),
                        new GooglePlaceAuthorAttribution(
                                "홍길동",
                                "https://maps.google.com/user/1",
                                "https://lh3.googleusercontent.com/a/1"
                        ),
                        "2026-04-01T12:34:56Z"
                ))
        );

        PlaceDetailResult result = PlaceDetailResult.from(place);

        assertThat(result.googlePlaceId()).isEqualTo("ChIJ123");
        assertThat(result.name()).isEqualTo("Cafe Layered");
        assertThat(result.formattedAddress()).isEqualTo("서울 종로구 ...");
        assertThat(result.location()).isEqualTo(new PlaceDetailResult.Location(37.57, 126.98));
        assertThat(result.primaryType()).isEqualTo("cafe");
        assertThat(result.primaryTypeDisplayName()).isEqualTo("카페");
        assertThat(result.rating()).isEqualTo(4.5);
        assertThat(result.userRatingCount()).isEqualTo(128);
        assertThat(result.phoneNumber()).isEqualTo("02-123-4567");
        assertThat(result.websiteUri()).isEqualTo("https://layered.example");
        assertThat(result.googleMapsUri()).isEqualTo("https://maps.google.com/?cid=123");
        assertThat(result.regularOpeningHours()).isEqualTo(new PlaceDetailResult.RegularOpeningHours(
                true,
                "DRIVE_THROUGH",
                List.of(new PlaceDetailResult.SpecialDay(
                        new PlaceDetailResult.Date(2026, 5, 5)
                )),
                List.of(new PlaceDetailResult.Period(
                        new PlaceDetailResult.TimePoint(1, 9, 0, null, false),
                        new PlaceDetailResult.TimePoint(1, 18, 0, null, true)
                )),
                List.of("월요일: 09:00~18:00"),
                "2026-04-30T00:00:00Z",
                "2026-04-29T09:00:00Z"
        ));
        assertThat(result.weekdayDescriptions()).containsExactly("월요일: 09:00~18:00");
        assertThat(result.photoNames()).containsExactly(
                "places/ChIJ123/photos/a",
                "places/ChIJ123/photos/b"
        );
        assertThat(result.reviewSummary()).isEqualTo("디저트와 분위기가 좋아요");
        assertThat(result.reviews()).containsExactly(new PlaceDetailResult.Review(
                5.0,
                "케이크가 맛있어요",
                "홍길동",
                "2026-04-01T12:34:56Z",
                "2주 전"
        ));
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
        assertThat(result.primaryTypeDisplayName()).isNull();
        assertThat(result.rating()).isNull();
        assertThat(result.userRatingCount()).isNull();
        assertThat(result.phoneNumber()).isNull();
        assertThat(result.websiteUri()).isNull();
        assertThat(result.googleMapsUri()).isNull();
        assertThat(result.regularOpeningHours()).isNull();
        assertThat(result.weekdayDescriptions()).isEmpty();
        assertThat(result.photoNames()).isEmpty();
        assertThat(result.reviewSummary()).isNull();
        assertThat(result.reviews()).isEmpty();
    }
}
