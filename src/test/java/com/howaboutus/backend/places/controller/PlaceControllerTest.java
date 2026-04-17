package com.howaboutus.backend.places.controller;

import com.howaboutus.backend.common.config.SecurityConfig;
import com.howaboutus.backend.common.error.ExternalApiException;
import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import com.howaboutus.backend.places.service.PlaceDetailService;
import com.howaboutus.backend.places.service.PlaceSearchService;
import com.howaboutus.backend.places.service.dto.PlaceDetailResult;
import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import java.util.List;
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

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceSearchService placeSearchService;

    @MockitoBean
    private PlaceDetailService placeDetailService;

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
                .andExpect(jsonPath("$.code").value("INVALID_PLACE_QUERY"))
                .andExpect(jsonPath("$.message").value("검색어는 공백일 수 없습니다"));

        verifyNoInteractions(placeSearchService);
    }

    @Test
    @DisplayName("query 파라미터가 없으면 400을 반환한다")
    void returnsBadRequestWhenQueryParameterIsMissing() throws Exception {
        mockMvc.perform(get(SEARCH_PATH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("필수 요청 파라미터가 누락되었습니다: query"));
    }

    @Test
    @DisplayName("유효한 query로 검색하면 결과를 반환한다")
    void returnsSearchResultsWhenQueryIsValid() throws Exception {
        given(placeSearchService.search(VALID_QUERY, null, null, null))
                .willReturn(List.of(placeSearchResult));

        mockMvc.perform(searchRequest(VALID_QUERY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].googlePlaceId").value("ChIJ123"))
                .andExpect(jsonPath("$[0].name").value("Cafe Layered"));

        then(placeSearchService).should().search(VALID_QUERY, null, null, null);
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
        given(placeSearchService.search(VALID_QUERY, null, null, null))
                .willReturn(List.of(resultWithoutLocation));

        mockMvc.perform(searchRequest(VALID_QUERY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].location").value(Matchers.nullValue()));

        then(placeSearchService).should().search(VALID_QUERY, null, null, null);
    }

    @Test
    @DisplayName("외부 API 오류 발생 시 502를 반환한다")
    void returnsBadGatewayWhenExternalApiErrorOccurs() throws Exception {
        given(placeSearchService.search(VALID_QUERY, null, null, null))
                .willThrow(new ExternalApiException(new RuntimeException("connection timeout")));

        mockMvc.perform(searchRequest(VALID_QUERY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXTERNAL_API_ERROR"))
                .andExpect(jsonPath("$.message").value("외부 API 호출 중 오류가 발생했습니다"));
    }

    @Test
    @DisplayName("처리되지 않은 예외 발생 시 500을 반환한다")
    void returnsInternalServerErrorForUnhandledException() throws Exception {
        given(placeSearchService.search(VALID_QUERY, null, null, null))
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

    private static MockHttpServletRequestBuilder searchRequest(String query) {
        return get(SEARCH_PATH).param("query", query);
    }
}
