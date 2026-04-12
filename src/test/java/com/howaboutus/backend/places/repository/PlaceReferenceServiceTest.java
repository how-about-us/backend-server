package com.howaboutus.backend.places.repository;

import com.howaboutus.backend.places.service.PlaceReferenceService;
import com.howaboutus.backend.support.AbstractPostgresContainerTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PlaceReferenceServiceTest extends AbstractPostgresContainerTest {

    @Autowired
    private PlaceReferenceService placeReferenceService;

    @Autowired
    private PlaceRepository placeRepository;

    @Test
    @DisplayName("신규 구글 장소 ID는 생성하고 기존 구글 장소 ID는 재사용한다")
    @Transactional
    void createsMissingPlaceReferencesAndReusesExistingOnes() {
        Map<String, Long> first = placeReferenceService.ensurePlaceIds(List.of("ChIJ1", "ChIJ2"));
        Map<String, Long> second = placeReferenceService.ensurePlaceIds(List.of("ChIJ2", "ChIJ3"));

        assertThat(first).containsKeys("ChIJ1", "ChIJ2");
        assertThat(second.get("ChIJ2")).isEqualTo(first.get("ChIJ2"));
        assertThat(placeRepository.count()).isEqualTo(3);
    }
}
