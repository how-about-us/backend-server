package com.howaboutus.backend.places.service.dto;

import com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceSearchResultTest {

    @Test
    @DisplayName("Google 장소 응답을 검색 결과로 매핑한다")
    void createsSearchResultFromPlaceItem() {
        GoogleTextSearchResponse.PlaceItem placeItem = new GoogleTextSearchResponse.PlaceItem(
                "ChIJ123",
                new GoogleTextSearchResponse.DisplayName("Cafe Layered", "ko"),
                "서울 종로구 ...",
                new GoogleTextSearchResponse.Location(37.57, 126.98),
                "cafe",
                new GoogleTextSearchResponse.LocalizedText("카페", "ko"),
                4.5,
                128,
                new GoogleTextSearchResponse.RegularOpeningHours(true),
                new GoogleTextSearchResponse.ReviewSummary(
                        new GoogleTextSearchResponse.LocalizedText("방문객들이 디저트를 좋아해요", "ko")
                ),
                List.of(new GoogleTextSearchResponse.Photo("places/ChIJ123/photos/abc"))
        );

        PlaceSearchResult result = PlaceSearchResult.from(placeItem);

        assertThat(result.googlePlaceId()).isEqualTo("ChIJ123");
        assertThat(result.name()).isEqualTo("Cafe Layered");
        assertThat(result.formattedAddress()).isEqualTo("서울 종로구 ...");
        assertThat(result.location()).isEqualTo(new PlaceSearchResult.Location(37.57, 126.98));
        assertThat(result.primaryType()).isEqualTo("cafe");
        assertThat(result.primaryTypeDisplayName()).isEqualTo("카페");
        assertThat(result.rating()).isEqualTo(4.5);
        assertThat(result.userRatingCount()).isEqualTo(128);
        assertThat(result.openNow()).isTrue();
        assertThat(result.reviewSummary()).isEqualTo("방문객들이 디저트를 좋아해요");
        assertThat(result.photoName()).isEqualTo("places/ChIJ123/photos/abc");
    }

    @Test
    @DisplayName("선택 필드가 없어도 null 안전하게 검색 결과를 만든다")
    void createsSearchResultWhenOptionalFieldsAreMissing() {
        GoogleTextSearchResponse.PlaceItem placeItem = new GoogleTextSearchResponse.PlaceItem(
                "ChIJ123",
                null,
                "서울 종로구 ...",
                null,
                "cafe",
                null,
                null,
                null,
                null,
                null,
                null
        );

        PlaceSearchResult result = PlaceSearchResult.from(placeItem);

        assertThat(result.googlePlaceId()).isEqualTo("ChIJ123");
        assertThat(result.name()).isNull();
        assertThat(result.location()).isNull();
        assertThat(result.primaryTypeDisplayName()).isNull();
        assertThat(result.rating()).isNull();
        assertThat(result.userRatingCount()).isNull();
        assertThat(result.openNow()).isNull();
        assertThat(result.reviewSummary()).isNull();
        assertThat(result.photoName()).isNull();
    }
}
