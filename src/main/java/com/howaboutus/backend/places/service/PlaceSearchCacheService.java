package com.howaboutus.backend.places.service;

import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlaceSearchCacheService {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final TypeReference<List<PlaceSearchResult>> PLACE_SEARCH_RESULTS_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public CacheLookup get(String query) {
        try {
            String value = redisTemplate.opsForValue().get(key(query));
            if (value == null) {
                return CacheLookup.miss();
            }

            List<PlaceSearchResult> results = objectMapper.readValue(value, PLACE_SEARCH_RESULTS_TYPE);
            return CacheLookup.hit(results);
        } catch (Exception e) {
            log.warn("Failed to read place search cache. query={}", query, e);
            return CacheLookup.miss();
        }
    }

    public void put(String query, List<PlaceSearchResult> results) {
        try {
            String serialized = objectMapper.writeValueAsString(results);
            redisTemplate.opsForValue().set(key(query), serialized, TTL);
        } catch (Exception e) {
            log.warn("Failed to write place search cache. query={}", query, e);
        }
    }

    private String key(String query) {
        return "places:search:" + normalize(query);
    }

    private String normalize(String query) {
        return query.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    public record CacheLookup(boolean hit, List<PlaceSearchResult> results) {

        public CacheLookup {
            if (results == null) {
                results = List.of();
            }
            results = List.copyOf(results);
        }

        public static CacheLookup miss() {
            return new CacheLookup(false, List.of());
        }

        public static CacheLookup hit(List<PlaceSearchResult> results) {
            return new CacheLookup(true, results);
        }
    }
}
