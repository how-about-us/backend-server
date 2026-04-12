package com.howaboutus.backend;

import com.howaboutus.backend.places.service.PlaceSearchService;
import com.howaboutus.backend.places.service.dto.PlaceSearchResult;
import com.howaboutus.backend.support.AbstractPostgresContainerTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HowAboutUsBackendApplicationTests extends AbstractPostgresContainerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceSearchService placeSearchService;

    @Test
    @DisplayName("애플리케이션 컨텍스트가 로드된다")
    void contextLoads() {
    }

    @Test
    @DisplayName("장소 검색 엔드포인트가 노출된다")
    void placeSearchEndpointIsExposed() throws Exception {
        given(placeSearchService.search("seoul cafe"))
                .willReturn(List.of(new PlaceSearchResult(
                        1L,
                        "ChIJ1",
                        "Cafe Layered",
                        "서울 종로구 ...",
                        new PlaceSearchResult.Location(37.57, 126.98),
                        "cafe",
                        4.5,
                        null
                )));

        mockMvc.perform(get("/places/search").param("query", "seoul cafe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].placeId").value(1L));
    }
}
