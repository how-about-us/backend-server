package com.howaboutus.backend.places.service;

import com.howaboutus.backend.places.controller.PlaceSearchResponse;
import java.util.List;

public interface PlaceSearchService {

    List<PlaceSearchResponse> search(String query);
}
