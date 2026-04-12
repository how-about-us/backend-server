package com.howaboutus.backend.places.controller;

import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlaceController {

    private final PlaceSearchService placeSearchService;

    public PlaceController(PlaceSearchService placeSearchService) {
        this.placeSearchService = placeSearchService;
    }

    @GetMapping("/places/search")
    public List<PlaceSearchResponse> search(@RequestParam String query) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query must not be blank");
        }
        return placeSearchService.search(query);
    }
}

interface PlaceSearchService {

    List<PlaceSearchResponse> search(String query);
}
