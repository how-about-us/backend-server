package com.howaboutus.backend.places.controller;

import com.howaboutus.backend.places.controller.dto.PlaceDetailResponse;
import com.howaboutus.backend.places.controller.dto.PlaceSearchResponse;
import com.howaboutus.backend.places.service.PlaceDetailService;
import com.howaboutus.backend.places.service.PlaceSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@Tag(name = "Places", description = "장소 검색 API")
@RestController
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceDetailService placeDetailService;
    private final PlaceSearchService placeSearchService;

    @Operation(
            summary = "장소 검색",
            description = "사용자 검색어를 기반으로 장소 후보 목록을 조회합니다. latitude/longitude를 제공하면 해당 좌표 근처 결과를 우선합니다."
    )
    @GetMapping("/places/search")
    public List<PlaceSearchResponse> search(
            @Parameter(description = "장소 검색어", example = "일본 맛집")
            @RequestParam
            @NotBlank(message = "검색어는 공백일 수 없습니다")
            String query,
            @Parameter(description = "현재 위치 위도 (longitude와 함께 제공)", example = "37.5")
            @RequestParam(required = false)
            Double latitude,
            @Parameter(description = "현재 위치 경도 (latitude와 함께 제공)", example = "127.0")
            @RequestParam(required = false)
            Double longitude,
            @Parameter(description = "검색 반경(m), 기본값 5000", example = "3000")
            @RequestParam(required = false, defaultValue = "5000.0")
            Double radius) {
        if ((latitude == null) != (longitude == null)) {
            throw new CustomException(ErrorCode.INVALID_LOCATION_PARAMS);
        }
        return placeSearchService.search(query, latitude, longitude, radius)
                .stream()
                .map(PlaceSearchResponse::from)
                .toList();
    }

    @Operation(
            summary = "장소 상세 조회",
            description = "googlePlaceId를 기반으로 장소 상세 정보를 조회합니다."
    )
    @GetMapping("/places/{googlePlaceId}")
    public PlaceDetailResponse getDetail(
            @Parameter(description = "Google Place ID", example = "ChIJ123")
            @PathVariable
            @NotBlank(message = "googlePlaceId는 공백일 수 없습니다")
            String googlePlaceId) {
        return PlaceDetailResponse.from(placeDetailService.getDetail(googlePlaceId));
    }
}
