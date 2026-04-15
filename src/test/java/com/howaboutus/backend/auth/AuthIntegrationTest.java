package com.howaboutus.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.auth.service.dto.GoogleUserInfo;
import com.howaboutus.backend.common.integration.google.GoogleOAuthClient;
import com.howaboutus.backend.support.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class AuthIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoogleOAuthClient googleOAuthClient;

    @Test
    @DisplayName("로그인 시 access_token과 refresh_token 쿠키를 발급한다")
    void loginIssuesTokenCookies() throws Exception {
        given(googleOAuthClient.login("auth-code-login"))
                .willReturn(new GoogleUserInfo("google-it-login", "login@gmail.com", "로그인", null));

        mockMvc.perform(post("/auth/google/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "auth-code-login"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true));
    }

    @Test
    @DisplayName("Refresh Token Rotation — 새 토큰을 발급하고 이전 토큰은 거절된다")
    void refreshRotatesTokenAndRejectsOld() throws Exception {
        given(googleOAuthClient.login("auth-code-rotate"))
                .willReturn(new GoogleUserInfo("google-it-rotate", "rotate@gmail.com", "로테이션", null));

        MvcResult loginResult = mockMvc.perform(post("/auth/google/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "auth-code-rotate"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        Cookie oldRefreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(oldRefreshCookie).isNotNull();

        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                        .cookie(oldRefreshCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refresh_token"))
                .andReturn();

        Cookie newRefreshCookie = refreshResult.getResponse().getCookie("refresh_token");
        assertThat(newRefreshCookie).isNotNull();
        assertThat(newRefreshCookie.getValue()).isNotEqualTo(oldRefreshCookie.getValue());

        // 이전 토큰 재사용 → 401
        mockMvc.perform(post("/auth/refresh")
                        .cookie(oldRefreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃 후 Refresh Token 으로 재요청하면 401을 반환한다")
    void logoutInvalidatesRefreshToken() throws Exception {
        given(googleOAuthClient.login("auth-code-logout"))
                .willReturn(new GoogleUserInfo("google-it-logout", "logout@gmail.com", "로그아웃", null));

        MvcResult loginResult = mockMvc.perform(post("/auth/google/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "auth-code-logout"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();

        mockMvc.perform(post("/auth/logout")
                        .cookie(refreshCookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("access_token", 0))
                .andExpect(cookie().maxAge("refresh_token", 0));

        // 로그아웃된 토큰으로 Refresh 요청 → 401
        mockMvc.perform(post("/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }
}
