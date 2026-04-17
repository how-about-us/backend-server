package com.howaboutus.backend.auth.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.auth.service.AuthService;
import com.howaboutus.backend.auth.service.dto.LoginResult;
import com.howaboutus.backend.common.config.SecurityConfig;
import com.howaboutus.backend.common.config.properties.JwtProperties;
import com.howaboutus.backend.common.config.properties.RefreshTokenProperties;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
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

    @MockitoBean
    private RefreshTokenProperties refreshTokenProperties;

    @Test
    @DisplayName("Google 로그인 성공 시 access_token과 refresh_token 쿠키를 반환한다")
    void returnsAccessAndRefreshTokenCookiesOnLogin() throws Exception {
        given(authService.googleLogin("valid-code"))
                .willReturn(new LoginResult("jwt-token", "1:refresh-uuid", 1L));
        given(jwtProperties.accessTokenExpiration()).willReturn(1800000L);
        given(refreshTokenProperties.expiration()).willReturn(1209600000L);

        mockMvc.perform(post("/auth/google/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "valid-code"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true));
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

    @Test
    @DisplayName("Refresh 요청 시 새 토큰 쿠키를 반환한다")
    void refreshReturnsNewCookies() throws Exception {
        given(authService.refresh("1:old-uuid"))
                .willReturn(new LoginResult("new-jwt", "1:new-uuid", 1L));
        given(jwtProperties.accessTokenExpiration()).willReturn(1800000L);
        given(refreshTokenProperties.expiration()).willReturn(1209600000L);

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", "1:old-uuid")))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"));
    }

    @Test
    @DisplayName("Refresh Token 쿠키 없이 Refresh 요청 시 401을 반환한다")
    void returns401WhenRefreshTokenCookieMissing() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃 시 쿠키를 삭제하고 204를 반환한다")
    void logoutDeletesCookiesAndReturns204() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie("refresh_token", "1:some-uuid")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("access_token", 0))
                .andExpect(cookie().maxAge("refresh_token", 0));

        verify(authService).logout("1:some-uuid");
    }

    @Test
    @DisplayName("Refresh Token 쿠키 없이 로그아웃 요청 시에도 204를 반환한다")
    void logoutWithoutCookieReturns204() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());
    }
}
