package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.integration.google.GooglePlaceSearchClient;
import com.howaboutus.backend.common.integration.google.dto.GooglePlaceDetailResponse;
import com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchResponse;
import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PlaceSearchServiceTest {

    @Mock
    private GooglePlaceSearchClient googlePlaceSearchClient;
    @InjectMocks
    private PlaceSearchService placeSearchService;

    @Test
    @DisplayName("Google 장소 응답을 검색 결과 목록으로 변환한다")
    void returnsMappedSearchResults() {
        GoogleTextSearchResponse.PlaceItem placeItem = new GoogleTextSearchResponse.PlaceItem(
                "ChIJ123",
                new GooglePlaceDetailResponse.DisplayName("Cafe Layered", "ko"),
                "서울 종로구 ...",
                new GooglePlaceDetailResponse.Location(37.57, 126.98),
                "cafe",
                new GooglePlaceDetailResponse.LocalizedText("카페", "ko"),
                4.5,
                128,
                null,
                null,
                List.of(new GooglePlaceDetailResponse.Photo("places/ChIJ123/photos/abc"))
        );
        given(googlePlaceSearchClient.search("seoul cafe", 37.5, 127.0, 5000.0))
                .willReturn(List.of(placeItem));

        List<PlaceSearchResult> results = placeSearchService.search("seoul cafe", 37.5, 127.0, 5000.0);

        assertThat(results).containsExactly(PlaceSearchResult.from(placeItem));
        then(googlePlaceSearchClient).should().search("seoul cafe", 37.5, 127.0, 5000.0);
    }

    @Test
    @DisplayName("Google 장소 응답이 비어 있으면 빈 목록을 반환한다")
    void returnsEmptyResultsWhenClientReturnsEmptyList() {
        given(googlePlaceSearchClient.search("seoul cafe", 37.5, 127.0, 5000.0))
                .willReturn(List.of());

        List<PlaceSearchResult> results = placeSearchService.search("seoul cafe", 37.5, 127.0, 5000.0);

        assertThat(results).isEmpty();
        then(googlePlaceSearchClient).should().search("seoul cafe", 37.5, 127.0, 5000.0);
    }

    @Test
    @DisplayName("좌표가 있으면 client에 좌표와 반경을 그대로 전달한다")
    void forwardsLocationToClient() {
        given(googlePlaceSearchClient.search("seoul cafe", 37.5, 127.0, 3000.0))
                .willReturn(List.of());

        placeSearchService.search("seoul cafe", 37.5, 127.0, 3000.0);

        then(googlePlaceSearchClient).should().search("seoul cafe", 37.5, 127.0, 3000.0);
    }
}
