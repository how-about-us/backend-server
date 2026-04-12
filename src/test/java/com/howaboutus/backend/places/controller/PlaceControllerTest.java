package com.howaboutus.backend.places.controller;

import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import com.howaboutus.backend.config.SecurityConfig;
import com.howaboutus.backend.places.service.PlaceSearchService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlaceController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceSearchService placeSearchService;

    @Test
    void returnsBadRequestWhenQueryIsBlank() throws Exception {
        mockMvc.perform(get("/places/search").param("query", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("query must not be blank"));

        verifyNoInteractions(placeSearchService);
    }

    @Test
    void requiresAuthenticationForNonPlaceRoutes() throws Exception {
        mockMvc.perform(get("/private"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsSearchResultsWhenQueryIsValid() throws Exception {
        given(placeSearchService.search("seoul cafe"))
                .willReturn(List.of(new PlaceSearchResponse(
                        1L,
                        "ChIJ123",
                        "Cafe Layered",
                        "서울 종로구 ...",
                        new PlaceSearchResponse.Location(37.57, 126.98),
                        "cafe",
                        4.5,
                        "places/ChIJ123/photos/abc"
                )));

        mockMvc.perform(get("/places/search").param("query", "seoul cafe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].placeId").value(1L))
                .andExpect(jsonPath("$[0].googlePlaceId").value("ChIJ123"))
                .andExpect(jsonPath("$[0].name").value("Cafe Layered"))
                .andExpect(jsonPath("$[0].formattedAddress").value("서울 종로구 ..."))
                .andExpect(jsonPath("$[0].location.lat").value(37.57))
                .andExpect(jsonPath("$[0].location.lng").value(126.98))
                .andExpect(jsonPath("$[0].primaryType").value("cafe"))
                .andExpect(jsonPath("$[0].rating").value(4.5))
                .andExpect(jsonPath("$[0].photoName").value("places/ChIJ123/photos/abc"));
    }
}
