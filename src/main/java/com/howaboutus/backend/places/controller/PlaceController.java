package com.howaboutus.backend.places.controller;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.places.controller.dto.PlaceSearchResponse;
import com.howaboutus.backend.places.service.PlaceSearchService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceSearchService placeSearchService;

    @GetMapping("/places/search")
    public List<PlaceSearchResponse> search(@RequestParam String query) {
        if (!StringUtils.hasText(query)) {
            throw new CustomException(ErrorCode.INVALID_PLACE_QUERY);
        }
        return placeSearchService.search(query).stream()
                .map(PlaceSearchResponse::from)
                .toList();
    }
}
