package com.howaboutus.backend.places.service;

import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DefaultPlaceSearchService implements PlaceSearchService {

    @Override
    public List<PlaceSearchResult> search(String query) {
        return List.of();
    }
}
