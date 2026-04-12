package com.howaboutus.backend.places.service;

import com.howaboutus.backend.places.service.dto.GoogleTextSearchResponse;
import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

class PlaceSearchServiceTest {

    private PlaceSearchCacheService cacheService;
    private GooglePlaceSearchClient googleClient;
    private PlaceReferenceService placeReferenceService;
    private PlaceSearchService placeSearchService;

    @BeforeEach
    void setUp() {
        cacheService = Mockito.mock(PlaceSearchCacheService.class);
        googleClient = Mockito.mock(GooglePlaceSearchClient.class);
        placeReferenceService = Mockito.mock(PlaceReferenceService.class);
        placeSearchService = new PlaceSearchService(cacheService, googleClient, placeReferenceService);
    }

    @Test
    @DisplayName("캐시에 결과가 있으면 Google API를 호출하지 않고 캐시 값을 반환한다")
    void returnsCachedResultsBeforeCallingGoogle() {
        List<PlaceSearchResult> cached = List.of(new PlaceSearchResult(
                1L,
                "ChIJ1",
                "Cafe Layered",
                "서울 종로구 ...",
                new PlaceSearchResult.Location(37.57, 126.98),
                "cafe",
                4.5,
                "places/ChIJ1/photos/1"
        ));
        given(cacheService.get("seoul cafe")).willReturn(cached);

        List<PlaceSearchResult> result = placeSearchService.search("seoul cafe");

        assertThat(result).isEqualTo(cached);
        then(googleClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("캐시 미스 시 Google API를 호출하고 내부 ID를 저장한 뒤 결과를 캐시에 저장한다")
    void fetchesFromGooglePersistsInternalIdsAndCachesMisses() {
        given(cacheService.get("seoul cafe")).willReturn(List.of());
        given(googleClient.search("seoul cafe")).willReturn(List.of(
                new GoogleTextSearchResponse.PlaceItem(
                        "ChIJ1",
                        new GoogleTextSearchResponse.DisplayName("Cafe Layered", "ko"),
                        "서울 종로구 ...",
                        new GoogleTextSearchResponse.Location(37.57, 126.98),
                        "cafe",
                        4.5,
                        List.of(new GoogleTextSearchResponse.Photo("places/ChIJ1/photos/1"))
                )
        ));
        given(placeReferenceService.ensurePlaceIds(List.of("ChIJ1")))
                .willReturn(Map.of("ChIJ1", 11L));

        List<PlaceSearchResult> result = placeSearchService.search("seoul cafe");

        assertThat(result.getFirst().placeId()).isEqualTo(11L);
        then(cacheService).should().put("seoul cafe", result);
    }
}
