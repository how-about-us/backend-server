package com.howaboutus.backend.places.service;

import com.howaboutus.backend.places.entity.Place;
import com.howaboutus.backend.places.repository.PlaceRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceReferenceService {

    private final PlaceRepository placeRepository;

    @Transactional
    public Map<String, Long> ensurePlaceIds(Collection<String> googlePlaceIds) {
        Map<String, Long> placeIds = new LinkedHashMap<>();
        placeRepository.findAllByGooglePlaceIdIn(googlePlaceIds)
                .forEach(place -> placeIds.put(place.getGooglePlaceId(), place.getId()));

        Set<String> missingPlaceIds = new LinkedHashSet<>(googlePlaceIds);
        missingPlaceIds.removeAll(placeIds.keySet());

        for (String googlePlaceId : missingPlaceIds) {
            try {
                Place saved = placeRepository.saveAndFlush(new Place(googlePlaceId));
                placeIds.put(saved.getGooglePlaceId(), saved.getId());
            } catch (DataIntegrityViolationException exception) {
                Place existing = placeRepository.findByGooglePlaceId(googlePlaceId)
                        .orElseThrow(() -> exception);
                placeIds.put(existing.getGooglePlaceId(), existing.getId());
            }
        }

        return placeIds;
    }
}
