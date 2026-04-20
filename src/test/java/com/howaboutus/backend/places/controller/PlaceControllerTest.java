package com.howaboutus.backend.places.controller;

import com.howaboutus.backend.common.config.SecurityConfig;
import com.howaboutus.backend.common.error.ExternalApiException;
import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import com.howaboutus.backend.places.service.PlaceDetailService;
import com.howaboutus.backend.places.service.PlacePhotoService;
import com.howaboutus.backend.places.service.PlaceSearchService;
import com.howaboutus.backend.places.service.dto.PlaceDetailResult;
import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlaceController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PlaceControllerTest {

    private static final String SEARCH_PATH = "/places/search";
    private static final String VALID_QUERY = "seoul cafe";
    private static final double DEFAULT_LAT = 37.5;
    private static final double DEFAULT_LNG = 127.0;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceSearchService placeSearchService;

    @MockitoBean
    private PlaceDetailService placeDetailService;

    @MockitoBean
    private PlacePhotoService placePhotoService;

    private PlaceSearchResult placeSearchResult;
    private PlaceDetailResult placeDetailResult;

    @BeforeEach
    void setUp() {
        placeSearchResult = new PlaceSearchResult(
                "ChIJ123",
                "Cafe Layered",
                "서울 종로구 ...",
                new PlaceSearchResult.Location(37.57, 126.98),
                "cafe",
                4.5,
                "places/ChIJ123/photos/abc"
        );
        placeDetailResult = new PlaceDetailResult(
                "ChIJ123",
                "Cafe Layered",
                "서울 종로구 ...",
                new PlaceDetailResult.Location(37.57, 126.98),
                "cafe",
                4.5,
                "02-123-4567",
                "https://layered.example",
                "https://maps.google.com/?cid=123",
                List.of("월요일: 09:00~18:00"),
                List.of("places/ChIJ123/photos/a", "places/ChIJ123/photos/b")
        );
    }

    @Test
    @DisplayName("빈 query로 검색하면 400을 반환한다")
    void returnsBadRequestWhenQueryIsBlank() throws Exception {
        mockMvc.perform(searchRequest("   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("검색어는 공백일 수 없습니다"));

        verifyNoInteractions(placeSearchService);
    }

    @Test
    @DisplayName("위도가 범위를 벗어나면 400을 반환한다")
    void returnsBadRequestWhenLatitudeIsOutOfRange() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .param("query", VALID_QUERY)
                        .param("latitude", "200")
                        .param("longitude", "127.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("위도는 90 이하이어야 합니다"));

        verifyNoInteractions(placeSearchService);
    }

    @Test
    @DisplayName("경도가 범위를 벗어나면 400을 반환한다")
    void returnsBadRequestWhenLongitudeIsOutOfRange() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .param("query", VALID_QUERY)
                        .param("latitude", "37.5")
                        .param("longitude", "-200"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("경도는 -180 이상이어야 합니다"));

        verifyNoInteractions(placeSearchService);
    }

    @Test
    @DisplayName("반경이 음수이면 400을 반환한다")
    void returnsBadRequestWhenRadiusIsNegative() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .param("query", VALID_QUERY)
                        .param("latitude", "37.5")
                        .param("longitude", "127.0")
                        .param("radius", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("반경은 0 이상이어야 합니다"));

        verifyNoInteractions(placeSearchService);
    }

    @Test
    @DisplayName("반경이 최대값(50000)을 초과하면 400을 반환한다")
    void returnsBadRequestWhenRadiusExceedsMaximum() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .param("query", VALID_QUERY)
                        .param("latitude", "37.5")
                        .param("longitude", "127.0")
                        .param("radius", "999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("반경은 50000 이하이어야 합니다"));

        verifyNoInteractions(placeSearchService);
    }

    @Test
    @DisplayName("query 파라미터가 없으면 400을 반환한다")
    void returnsBadRequestWhenQueryParameterIsMissing() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .param("latitude", "37.5")
                        .param("longitude", "127.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("필수 요청 파라미터가 누락되었습니다: query"));
    }

    @Test
    @DisplayName("latitude 파라미터가 없으면 400을 반환한다")
    void returnsBadRequestWhenLatitudeParameterIsMissing() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .param("query", VALID_QUERY)
                        .param("longitude", "127.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("필수 요청 파라미터가 누락되었습니다: latitude"));
    }

    @Test
    @DisplayName("longitude 파라미터가 없으면 400을 반환한다")
    void returnsBadRequestWhenLongitudeParameterIsMissing() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .param("query", VALID_QUERY)
                        .param("latitude", "37.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("필수 요청 파라미터가 누락되었습니다: longitude"));
    }

    @Test
    @DisplayName("유효한 query와 위치로 검색하면 서비스를 호출하고 결과를 반환한다")
    void returnsSearchResultsWhenQueryAndLocationAreValid() throws Exception {
        given(placeSearchService.search(VALID_QUERY, DEFAULT_LAT, DEFAULT_LNG, 5000.0))
                .willReturn(List.of(placeSearchResult));

        mockMvc.perform(searchRequest(VALID_QUERY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].googlePlaceId").value("ChIJ123"))
                .andExpect(jsonPath("$[0].name").value("Cafe Layered"));

        then(placeSearchService).should().search(VALID_QUERY, DEFAULT_LAT, DEFAULT_LNG, 5000.0);
    }

    @Test
    @DisplayName("latitude와 longitude, radius를 모두 제공하면 해당 위치와 반경으로 서비스를 호출한다")
    void callsServiceWithLocationWhenAllLocationParamsProvided() throws Exception {
        given(placeSearchService.search(VALID_QUERY, 37.5, 127.0, 3000.0))
                .willReturn(List.of(placeSearchResult));

        mockMvc.perform(get(SEARCH_PATH)
                        .param("query", VALID_QUERY)
                        .param("latitude", "37.5")
                        .param("longitude", "127.0")
                        .param("radius", "3000.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].googlePlaceId").value("ChIJ123"));

        then(placeSearchService).should().search(VALID_QUERY, 37.5, 127.0, 3000.0);
    }

    @Test
    @DisplayName("radius를 제공하지 않으면 기본 반경 5000m로 서비스를 호출한다")
    void callsServiceWithDefaultRadiusWhenRadiusNotProvided() throws Exception {
        given(placeSearchService.search(VALID_QUERY, 37.5, 127.0, 5000.0))
                .willReturn(List.of(placeSearchResult));

        mockMvc.perform(get(SEARCH_PATH)
                        .param("query", VALID_QUERY)
                        .param("latitude", "37.5")
                        .param("longitude", "127.0"))
                .andExpect(status().isOk());

        then(placeSearchService).should().search(VALID_QUERY, 37.5, 127.0, 5000.0);
    }

    @Test
    @DisplayName("검색 결과에 좌표가 없으면 location을 null로 반환한다")
    void returnsNullLocationWhenSearchResultDoesNotContainCoordinates() throws Exception {
        PlaceSearchResult resultWithoutLocation = new PlaceSearchResult(
                "ChIJ123",
                "Cafe Layered",
                "서울 종로구 ...",
                null,
                "cafe",
                4.5,
                "places/ChIJ123/photos/abc"
        );
        given(placeSearchService.search(VALID_QUERY, DEFAULT_LAT, DEFAULT_LNG, 5000.0))
                .willReturn(List.of(resultWithoutLocation));

        mockMvc.perform(searchRequest(VALID_QUERY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].location").value(Matchers.nullValue()));

        then(placeSearchService).should().search(VALID_QUERY, DEFAULT_LAT, DEFAULT_LNG, 5000.0);
    }

    @Test
    @DisplayName("외부 API 오류 발생 시 502를 반환한다")
    void returnsBadGatewayWhenExternalApiErrorOccurs() throws Exception {
        given(placeSearchService.search(VALID_QUERY, DEFAULT_LAT, DEFAULT_LNG, 5000.0))
                .willThrow(new ExternalApiException(new RuntimeException("connection timeout")));

        mockMvc.perform(searchRequest(VALID_QUERY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXTERNAL_API_ERROR"))
                .andExpect(jsonPath("$.message").value("외부 API 호출 중 오류가 발생했습니다"));
    }

    @Test
    @DisplayName("처리되지 않은 예외 발생 시 500을 반환한다")
    void returnsInternalServerErrorForUnhandledException() throws Exception {
        given(placeSearchService.search(VALID_QUERY, DEFAULT_LAT, DEFAULT_LNG, 5000.0))
                .willThrow(new RuntimeException("예상치 못한 오류"));

        mockMvc.perform(searchRequest(VALID_QUERY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다"));
    }

    @Test
    @DisplayName("유효한 googlePlaceId로 장소 상세 조회 시 결과를 반환한다")
    void returnsPlaceDetailWhenGooglePlaceIdIsValid() throws Exception {
        given(placeDetailService.getDetail("ChIJ123"))
                .willReturn(placeDetailResult);

        mockMvc.perform(get("/places/{googlePlaceId}", "ChIJ123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googlePlaceId").value("ChIJ123"))
                .andExpect(jsonPath("$.phoneNumber").value("02-123-4567"))
                .andExpect(jsonPath("$.photoNames[0]").value("places/ChIJ123/photos/a"));

        then(placeDetailService).should().getDetail("ChIJ123");
    }

    @Test
    @DisplayName("장소 상세 조회 중 외부 API 오류 발생 시 502를 반환한다")
    void returnsBadGatewayWhenPlaceDetailLookupFails() throws Exception {
        given(placeDetailService.getDetail("ChIJ123"))
                .willThrow(new ExternalApiException(new RuntimeException("connection timeout")));

        mockMvc.perform(get("/places/{googlePlaceId}", "ChIJ123"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXTERNAL_API_ERROR"))
                .andExpect(jsonPath("$.message").value("외부 API 호출 중 오류가 발생했습니다"));
    }

    @Test
    @DisplayName("유효한 name으로 요청하면 photoUrl을 반환한다")
    void returnsPhotoUrlForValidName() throws Exception {
        given(placePhotoService.getPhotoUrl("places/ChIJ123/photos/abc"))
                .willReturn("https://lh3.googleusercontent.com/photo.jpg");

        mockMvc.perform(get("/places/photos")
                        .param("name", "places/ChIJ123/photos/abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value("https://lh3.googleusercontent.com/photo.jpg"));

        then(placePhotoService).should().getPhotoUrl("places/ChIJ123/photos/abc");
    }

    @Test
    @DisplayName("빈 name으로 요청하면 400을 반환한다")
    void returnsBadRequestWhenNameIsBlank() throws Exception {
        mockMvc.perform(get("/places/photos")
                        .param("name", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("name은 공백일 수 없습니다"));

        verifyNoInteractions(placePhotoService);
    }

    @Test
    @DisplayName("name 파라미터가 없으면 400을 반환한다")
    void returnsBadRequestWhenNameParameterIsMissing() throws Exception {
        mockMvc.perform(get("/places/photos"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("필수 요청 파라미터가 누락되었습니다: name"));

        verifyNoInteractions(placePhotoService);
    }

    @Test
    @DisplayName("사진 URL 조회 중 외부 API 오류 발생 시 502를 반환한다")
    void returnsBadGatewayWhenPhotoUrlLookupFails() throws Exception {
        given(placePhotoService.getPhotoUrl("places/ChIJ123/photos/abc"))
                .willThrow(new ExternalApiException(new RuntimeException("connection timeout")));

        mockMvc.perform(get("/places/photos")
                        .param("name", "places/ChIJ123/photos/abc"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXTERNAL_API_ERROR"))
                .andExpect(jsonPath("$.message").value("외부 API 호출 중 오류가 발생했습니다"));
    }

    private static MockHttpServletRequestBuilder searchRequest(String query) {
        return get(SEARCH_PATH)
                .param("query", query)
                .param("latitude", String.valueOf(DEFAULT_LAT))
                .param("longitude", String.valueOf(DEFAULT_LNG));
    }
}
