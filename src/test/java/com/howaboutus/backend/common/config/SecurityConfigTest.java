package com.howaboutus.backend.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.auth.controller.AuthController;
import com.howaboutus.backend.auth.filter.JwtAuthenticationFilter;
import com.howaboutus.backend.common.security.JwtAuthenticationEntryPoint;
import com.howaboutus.backend.auth.service.AuthService;
import com.howaboutus.backend.auth.service.JwtProvider;
import com.howaboutus.backend.common.config.properties.JwtProperties;
import com.howaboutus.backend.common.config.properties.RefreshTokenProperties;
import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @MockitoBean
    private RefreshTokenProperties refreshTokenProperties;

    @Test
    @DisplayName("인증 없이 /users/me 접근 시 401을 반환한다")
    void returns401ForUnauthenticatedUsersMe() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
