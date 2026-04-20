package com.howaboutus.backend.places.controller;

import com.howaboutus.backend.places.controller.dto.PlaceDetailResponse;
import com.howaboutus.backend.places.controller.dto.PlacePhotoResponse;
import com.howaboutus.backend.places.controller.dto.PlaceSearchResponse;
import com.howaboutus.backend.places.service.PlaceDetailService;
import com.howaboutus.backend.places.service.PlacePhotoService;
import com.howaboutus.backend.places.service.PlaceSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
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
    private final PlacePhotoService placePhotoService;

    @Operation(
            summary = "장소 검색",
            description = "사용자 검색어와 현재 위치를 기반으로 주변 장소 후보 목록을 조회합니다."
    )
    @GetMapping("/places/search")
    public List<PlaceSearchResponse> search(
            @Parameter(description = "장소 검색어", example = "일본 맛집")
            @RequestParam
            @NotBlank(message = "검색어는 공백일 수 없습니다")
            String query,
            @Parameter(description = "현재 위치 위도", example = "37.5")
            @RequestParam
            @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다")
            @DecimalMax(value = "90.0", message = "위도는 90 이하이어야 합니다")
            double latitude,
            @Parameter(description = "현재 위치 경도", example = "127.0")
            @RequestParam
            @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다")
            @DecimalMax(value = "180.0", message = "경도는 180 이하이어야 합니다")
            double longitude,
            @Parameter(description = "검색 반경(m), 기본값 5000", example = "3000")
            @RequestParam(required = false, defaultValue = "5000.0")
            @DecimalMin(value = "0.0", message = "반경은 0 이상이어야 합니다")
            @DecimalMax(value = "50000.0", message = "반경은 50000 이하이어야 합니다")
            double radius) {
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

    @Operation(
            summary = "장소 사진 URL 조회",
            description = "photoName을 기반으로 Google 장소 사진 URL을 조회합니다."
    )
    @GetMapping("/places/photos")
    public PlacePhotoResponse getPhotoUrl(
            @Parameter(description = "Google 장소 사진 리소스 이름", example = "places/ChIJ123/photos/abc")
            @RequestParam
            @Pattern(regexp = "^places/[^/]+/photos/[^/]+$", message = "유효하지 않은 photoName 형식입니다")
            String name) {
        return new PlacePhotoResponse(placePhotoService.getPhotoUrl(name));
    }
}
