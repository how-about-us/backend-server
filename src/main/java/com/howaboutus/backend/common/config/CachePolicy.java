package com.howaboutus.backend.common.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

/**
 * 캐시 정책은 모두 여기서 관리합니다.
 *
 * @author Minhyung Kim
 * @see CacheConfig
 */
@Getter
@RequiredArgsConstructor
public enum CachePolicy {

    PLACE_DETAIL(Keys.PLACE_DETAIL, Duration.ofMinutes(5)),
    ROUTE(Keys.ROUTE, Duration.ofMinutes(3));

    public static final class Keys {
        private Keys() {
        }

        public static final String PLACE_DETAIL = "place:detail";
        public static final String ROUTE = "route";
    }

    private final String key;
    private final Duration duration;
}
