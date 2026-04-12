package com.howaboutus.backend.places.controller;

import com.howaboutus.backend.common.config.SecurityConfig;
import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import com.howaboutus.backend.places.service.PlaceSearchService;
import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import java.util.List;
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

    private PlaceSearchResult placeSearchResult;

    @BeforeEach
    void setUp() {
        placeSearchResult = new PlaceSearchResult(
                1L,
                "ChIJ123",
                "Cafe Layered",
                "서울 종로구 ...",
                new PlaceSearchResult.Location(37.57, 126.98),
                "cafe",
                4.5,
                "places/ChIJ123/photos/abc"
        );
    }

    @Test
    @DisplayName("빈 query로 검색하면 400을 반환한다")
    void returnsBadRequestWhenQueryIsBlank() throws Exception {
        mockMvc.perform(searchRequest("   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLACE_QUERY"))
                .andExpect(jsonPath("$.message").value("query must not be blank"));

        verifyNoInteractions(placeSearchService);
    }

    @Test
    @DisplayName("query 파라미터가 없으면 400을 반환한다")
    void returnsBadRequestWhenQueryParameterIsMissing() throws Exception {
        mockMvc.perform(get(SEARCH_PATH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("missing required request parameter: query"));
    }

    @Test
    @DisplayName("장소 검색 외 경로는 인증이 필요하다")
    void requiresAuthenticationForNonPlaceRoutes() throws Exception {
        mockMvc.perform(get("/private"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("유효한 query로 검색하면 결과를 반환한다")
    void returnsSearchResultsWhenQueryIsValid() throws Exception {
        given(placeSearchService.search(VALID_QUERY))
                .willReturn(List.of(placeSearchResult));

        mockMvc.perform(searchRequest(VALID_QUERY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].googlePlaceId").value("ChIJ123"))
                .andExpect(jsonPath("$[0].name").value("Cafe Layered"));

        then(placeSearchService).should().search(VALID_QUERY);
    }

    private static MockHttpServletRequestBuilder searchRequest(String query) {
        return get(SEARCH_PATH).param("query", query);
    }
}
