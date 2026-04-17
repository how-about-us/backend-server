package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.integration.google.GooglePlaceSearchClient;
import com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchResponse;
import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class PlaceSearchServiceTest {

    private final GooglePlaceSearchClient googlePlaceSearchClient = mock(GooglePlaceSearchClient.class);
    private final PlaceSearchService placeSearchService = new PlaceSearchService(googlePlaceSearchClient);

    @Test
    @DisplayName("Google 장소 응답을 검색 결과 목록으로 변환한다")
    void returnsMappedSearchResults() {
        GoogleTextSearchResponse.PlaceItem placeItem = new GoogleTextSearchResponse.PlaceItem(
                "ChIJ123",
                new GoogleTextSearchResponse.DisplayName("Cafe Layered", "ko"),
                "서울 종로구 ...",
                new GoogleTextSearchResponse.Location(37.57, 126.98),
                "cafe",
                4.5,
                List.of(new GoogleTextSearchResponse.Photo("places/ChIJ123/photos/abc"))
        );
        given(googlePlaceSearchClient.search("seoul cafe", null, null, null)).willReturn(List.of(placeItem));

        List<PlaceSearchResult> results = placeSearchService.search("seoul cafe");

        assertThat(results).containsExactly(PlaceSearchResult.from(placeItem));
        then(googlePlaceSearchClient).should().search("seoul cafe", null, null, null);
    }

    @Test
    @DisplayName("Google 장소 응답이 비어 있으면 빈 목록을 반환한다")
    void returnsEmptyResultsWhenClientReturnsEmptyList() {
        given(googlePlaceSearchClient.search("seoul cafe", null, null, null)).willReturn(List.of());

        List<PlaceSearchResult> results = placeSearchService.search("seoul cafe");

        assertThat(results).isEmpty();
        then(googlePlaceSearchClient).should().search("seoul cafe", null, null, null);
    }
}
