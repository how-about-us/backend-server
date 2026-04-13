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

    ;

    public static final class Keys {
        private Keys() {
        }
    }

    private final String key;
    private final Duration duration;
}
