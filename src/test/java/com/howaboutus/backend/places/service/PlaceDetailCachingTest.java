package com.howaboutus.backend.places.service;

import com.howaboutus.backend.common.config.CachePolicy;
import com.howaboutus.backend.common.integration.google.GooglePlaceDetailClient;
import com.howaboutus.backend.common.integration.google.dto.GooglePlaceDetailResponse;
import com.howaboutus.backend.places.service.dto.PlaceDetailResult;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;

@SpringJUnitConfig(classes = PlaceDetailCachingTest.TestConfig.class)
class PlaceDetailCachingTest {

    @Autowired
    private PlaceDetailService placeDetailService;

    @Autowired
    private GooglePlaceDetailClient googlePlaceDetailClient;

    @Autowired
    private ToggleableCache toggleableCache;

    @BeforeEach
    void setUp() {
        reset(googlePlaceDetailClient);
        toggleableCache.clear();
        toggleableCache.setFailOnAccess(false);
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

    @Test
    @DisplayName("캐시 읽기 쓰기에서 예외가 나도 외부 조회로 폴백한다")
    void fallsBackToGoogleClientWhenCacheAccessFails() {
        given(googlePlaceDetailClient.getDetail("ChIJ123")).willReturn(detailResponse());
        toggleableCache.setFailOnAccess(true);

        PlaceDetailResult result = placeDetailService.getDetail("ChIJ123");

        assertThat(result.googlePlaceId()).isEqualTo("ChIJ123");
        then(googlePlaceDetailClient).should().getDetail("ChIJ123");
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

    @Configuration
    @EnableCaching
    static class TestConfig implements CachingConfigurer {

        @Bean
        ToggleableCache toggleableCache() {
            return new ToggleableCache(CachePolicy.Keys.PLACE_DETAIL);
        }

        @Bean
        @Override
        public CacheManager cacheManager() {
            SimpleCacheManager cacheManager = new SimpleCacheManager();
            cacheManager.setCaches(List.of(toggleableCache()));
            return cacheManager;
        }

        @Bean
        @Override
        public CacheErrorHandler errorHandler() {
            return new LoggingCacheErrorHandler(LogFactory.getLog(PlaceDetailCachingTest.class), true);
        }

        @Bean
        GooglePlaceDetailClient googlePlaceDetailClient() {
            return mock(GooglePlaceDetailClient.class);
        }

        @Bean
        PlaceDetailService placeDetailService(GooglePlaceDetailClient googlePlaceDetailClient) {
            return new PlaceDetailService(googlePlaceDetailClient);
        }
    }

    static class ToggleableCache implements Cache {

        private final ConcurrentMapCache delegate;
        private boolean failOnAccess;

        ToggleableCache(String name) {
            this.delegate = new ConcurrentMapCache(name);
        }

        void setFailOnAccess(boolean failOnAccess) {
            this.failOnAccess = failOnAccess;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Object getNativeCache() {
            return delegate.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object key) {
            throwIfNeeded();
            return delegate.get(key);
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            throwIfNeeded();
            return delegate.get(key, type);
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            throwIfNeeded();
            return delegate.get(key, valueLoader);
        }

        @Override
        public void put(Object key, Object value) {
            throwIfNeeded();
            delegate.put(key, value);
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            throwIfNeeded();
            return delegate.putIfAbsent(key, value);
        }

        @Override
        public void evict(Object key) {
            delegate.evict(key);
        }

        @Override
        public boolean evictIfPresent(Object key) {
            return delegate.evictIfPresent(key);
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public boolean invalidate() {
            return delegate.invalidate();
        }

        private void throwIfNeeded() {
            if (failOnAccess) {
                throw new IllegalStateException("cache access failed");
            }
        }
    }
}
