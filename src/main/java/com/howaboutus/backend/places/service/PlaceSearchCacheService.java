package com.howaboutus.backend.places.service;

import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PlaceSearchCacheService {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, Object> redisTemplate;

    @SuppressWarnings("unchecked")
    public List<PlaceSearchResult> get(String query) {
        try {
            Object value = redisTemplate.opsForValue().get(key(query));
            if (value == null) {
                return List.of();
            }
            return (List<PlaceSearchResult>) value;
        } catch (Exception e) {
            return List.of();
        }
    }

    public void put(String query, List<PlaceSearchResult> results) {
        redisTemplate.opsForValue().set(key(query), results, TTL);
    }

    private String key(String query) {
        return "places:search:" + normalize(query);
    }

    private String normalize(String query) {
        return query.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
