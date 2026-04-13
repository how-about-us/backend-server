package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.config.CachePolicy;
import com.howaboutus.backend.common.integration.google.GooglePlaceSearchClient;
import com.howaboutus.backend.common.integration.google.dto.GoogleTextSearchResponse;
import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import com.howaboutus.backend.support.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

class PlaceSearchServiceTest extends BaseIntegrationTest {

    @MockitoBean
    private GooglePlaceSearchClient googleClient;

    @MockitoBean
    private PlaceReferenceService placeReferenceService;

    @Autowired
    private PlaceSearchService placeSearchService;

    @Autowired
    private CacheManager cacheManager;

    @AfterEach
    void tearDown() {
        cacheManager.getCache(CachePolicy.Keys.PLACES_SEARCH).clear();
    }

    @Test
    @DisplayName("같은 검색어로 두 번 조회하면 두 번째 조회는 캐시에서 반환한다")
    void returnsCachedResultsOnSecondLookup() {
        String query = "seoul cafe";
        given(googleClient.search(query)).willReturn(List.of(placeItem()));
        given(placeReferenceService.ensurePlaceIds(List.of("ChIJ1")))
                .willReturn(Map.of("ChIJ1", 11L));

        List<PlaceSearchResult> first = placeSearchService.search(query);
        List<PlaceSearchResult> second = placeSearchService.search(query);

        assertThat(second).isEqualTo(first);
        then(googleClient).should(times(1)).search(query);
        then(placeReferenceService).should(times(1)).ensurePlaceIds(List.of("ChIJ1"));
    }

    @Test
    @DisplayName("검색어 정규화 결과가 같으면 같은 캐시 키를 사용한다")
    void usesNormalizedCacheKey() {
        given(googleClient.search("  SeOul   cafe  ")).willReturn(List.of(placeItem()));
        given(placeReferenceService.ensurePlaceIds(List.of("ChIJ1")))
                .willReturn(Map.of("ChIJ1", 11L));

        List<PlaceSearchResult> first = placeSearchService.search("  SeOul   cafe  ");
        List<PlaceSearchResult> second = placeSearchService.search("seoul cafe");

        assertThat(second).isEqualTo(first);
        then(googleClient).should(times(1)).search("  SeOul   cafe  ");
        then(placeReferenceService).should(times(1)).ensurePlaceIds(List.of("ChIJ1"));
    }

    @Test
    @DisplayName("빈 결과도 캐시되어 같은 검색어 재조회 시 Google API를 다시 호출하지 않는다")
    void cachesEmptyResults() {
        given(googleClient.search("seoul cafe")).willReturn(List.of());
        given(placeReferenceService.ensurePlaceIds(List.of())).willReturn(Map.of());

        List<PlaceSearchResult> first = placeSearchService.search("seoul cafe");
        List<PlaceSearchResult> second = placeSearchService.search("seoul cafe");

        assertThat(first).isEmpty();
        assertThat(second).isEmpty();
        then(googleClient).should(times(1)).search("seoul cafe");
        then(placeReferenceService).should(times(1)).ensurePlaceIds(List.of());
    }

    private static GoogleTextSearchResponse.PlaceItem placeItem() {
        return new GoogleTextSearchResponse.PlaceItem(
                "ChIJ1",
                new GoogleTextSearchResponse.DisplayName("Cafe Layered", "ko"),
                "서울 종로구 ...",
                new GoogleTextSearchResponse.Location(37.57, 126.98),
                "cafe",
                4.5,
                List.of(new GoogleTextSearchResponse.Photo("places/" + "ChIJ1" + "/photos/1"))
        );
    }
}
