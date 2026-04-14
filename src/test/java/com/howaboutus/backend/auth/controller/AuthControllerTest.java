package com.howaboutus.backend.auth.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.auth.service.AuthService;
import com.howaboutus.backend.common.config.SecurityConfig;
import com.howaboutus.backend.common.config.properties.JwtProperties;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @Test
    @DisplayName("Google 로그인 성공 시 200과 access_token 쿠키를 반환한다")
    void returnsAccessTokenCookieOnSuccess() throws Exception {
        given(authService.googleLogin("valid-code")).willReturn("jwt-token");

        mockMvc.perform(post("/auth/google/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "valid-code"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().httpOnly("access_token", true));
    }

    @Test
    @DisplayName("Google 인증 실패 시 401을 반환한다")
    void returns401WhenGoogleAuthFails() throws Exception {
        given(authService.googleLogin("bad-code"))
                .willThrow(new CustomException(ErrorCode.GOOGLE_AUTH_FAILED));

        mockMvc.perform(post("/auth/google/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "bad-code"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
