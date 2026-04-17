package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.config.CachePolicy;
import com.howaboutus.backend.common.integration.google.GooglePlaceDetailClient;
import com.howaboutus.backend.common.integration.google.dto.GooglePlaceDetailResponse;
import com.howaboutus.backend.places.service.dto.PlaceDetailResult;
import com.howaboutus.backend.support.BaseIntegrationTest;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;

class PlaceDetailCachingTest extends BaseIntegrationTest {

    @Autowired
    private PlaceDetailService placeDetailService;

    @MockitoBean
    private GooglePlaceDetailClient googlePlaceDetailClient;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        reset(googlePlaceDetailClient);
        Objects.requireNonNull(cacheManager.getCache(CachePolicy.Keys.PLACE_DETAIL)).clear();
    }

    @Test
    @DisplayName("같은 googlePlaceId로 두 번 조회하면 캐시된 결과를 재사용한다")
    void reusesCachedResultForSameGooglePlaceId() {
        given(googlePlaceDetailClient.getDetail("ChIJ123")).willReturn(detailResponse());

        PlaceDetailResult first = placeDetailService.getDetail("ChIJ123");
        PlaceDetailResult second = placeDetailService.getDetail("ChIJ123");

        assertThat(first).isEqualTo(second);
        then(googlePlaceDetailClient).should(times(1)).getDetail("ChIJ123");
    }

    private GooglePlaceDetailResponse detailResponse() {
        return new GooglePlaceDetailResponse(
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
    }
}
