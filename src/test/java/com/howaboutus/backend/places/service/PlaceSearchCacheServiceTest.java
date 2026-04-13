package com.howaboutus.backend.places.service;

import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;

class PlaceSearchCacheServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private PlaceSearchCacheService cacheService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOperations = Mockito.mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        cacheService = new PlaceSearchCacheService(redisTemplate, new ObjectMapper());
    }

    @Test
    @DisplayName("Redis에 저장된 빈 배열은 cache hit로 반환한다")
    void returnsCachedEmptyResultsWhenRedisContainsEmptyArray() {
        given(valueOperations.get("places:search:seoul cafe")).willReturn("[]");

        PlaceSearchCacheService.CacheLookup result = cacheService.get("seoul cafe");

        assertThat(result.hit()).isTrue();
        assertThat(result.results()).isEmpty();
    }

    @Test
    @DisplayName("역직렬화에 실패하면 cache miss로 처리한다")
    void returnsCacheMissWhenDeserializationFails() {
        given(valueOperations.get("places:search:seoul cafe")).willReturn("{not-json}");

        PlaceSearchCacheService.CacheLookup result = cacheService.get("seoul cafe");

        assertThat(result.hit()).isFalse();
        assertThat(result.results()).isEmpty();
    }

    @Test
    @DisplayName("Redis 저장 중 예외가 발생해도 검색 결과 반환 흐름은 깨지지 않는다")
    void doesNotThrowWhenRedisWriteFails() {
        List<PlaceSearchResult> results = List.of(new PlaceSearchResult(
                1L,
                "ChIJ1",
                "Cafe Layered",
                "서울 종로구 ...",
                new PlaceSearchResult.Location(37.57, 126.98),
                "cafe",
                4.5,
                "places/ChIJ1/photos/1"
        ));
        Mockito.doThrow(new RuntimeException("redis down"))
                .when(valueOperations)
                .set(Mockito.eq("places:search:seoul cafe"), Mockito.anyString(), Mockito.any(java.time.Duration.class));

        assertThatCode(() -> cacheService.put("seoul cafe", results))
                .doesNotThrowAnyException();
    }
}
