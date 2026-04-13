package com.howaboutus.backend.places.repository;

import com.howaboutus.backend.places.entity.Place;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findByGooglePlaceId(String googlePlaceId);

    List<Place> findAllByGooglePlaceIdIn(Collection<String> googlePlaceIds);
}
