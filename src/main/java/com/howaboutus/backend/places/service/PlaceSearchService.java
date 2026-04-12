package com.howaboutus.backend.places.service;

import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import java.util.List;

public interface PlaceSearchService {

    List<PlaceSearchResult> search(String query);
}
