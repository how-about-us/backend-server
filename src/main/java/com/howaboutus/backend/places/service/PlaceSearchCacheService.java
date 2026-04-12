package com.howaboutus.backend.places.service;

import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.SerializationUtils;

@Service
@RequiredArgsConstructor
public class PlaceSearchCacheService {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public List<PlaceSearchResult> get(String query) {
        try {
            String payload = redisTemplate.opsForValue().get(key(query));
            if (payload == null) {
                return null;
            }
            byte[] bytes = Base64.getDecoder().decode(payload.getBytes(StandardCharsets.UTF_8));
            return deserialize(bytes);
        } catch (Exception exception) {
            return null;
        }
    }

    public void put(String query, List<PlaceSearchResult> results) {
        try {
            byte[] bytes = SerializationUtils.serialize(results);
            if (bytes != null) {
                String payload = Base64.getEncoder().encodeToString(bytes);
                redisTemplate.opsForValue().set(key(query), payload, TTL);
            }
        } catch (Exception ignored) {
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

    @SuppressWarnings("unchecked")
    private List<PlaceSearchResult> deserialize(byte[] bytes) {
        return (List<PlaceSearchResult>) SerializationUtils.deserialize(bytes);
    }
}
