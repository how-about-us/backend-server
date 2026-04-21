package com.howaboutus.backend.user.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.howaboutus.backend.auth.filter.JwtAuthenticationFilter;
import com.howaboutus.backend.common.security.JwtAuthenticationEntryPoint;
import com.howaboutus.backend.auth.service.JwtProvider;
import com.howaboutus.backend.common.config.SecurityConfig;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.common.error.GlobalExceptionHandler;
import com.howaboutus.backend.user.service.UserService;
import com.howaboutus.backend.user.service.dto.UserResponse;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("인증된 사용자가 GET /users/me 요청 시 프로필을 반환한다")
    void returnsProfileForAuthenticatedUser() throws Exception {
        given(jwtProvider.extractUserId("valid-jwt")).willReturn(1L);
        given(userService.getMyProfile(1L))
                .willReturn(new UserResponse(1L, "test@gmail.com", "닉네임", "https://img.url/photo.jpg", "GOOGLE"));

        mockMvc.perform(get("/users/me")
                        .cookie(new Cookie("access_token", "valid-jwt")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("test@gmail.com"))
                .andExpect(jsonPath("$.nickname").value("닉네임"))
                .andExpect(jsonPath("$.profileImageUrl").value("https://img.url/photo.jpg"))
                .andExpect(jsonPath("$.provider").value("GOOGLE"));
    }

    @Test
    @DisplayName("인증 없이 GET /users/me 요청 시 401을 반환한다")
    void returns401WithoutAuthentication() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("만료된 토큰으로 GET /users/me 요청 시 401과 ACCESS_TOKEN_EXPIRED를 반환한다")
    void returns401WithExpiredToken() throws Exception {
        given(jwtProvider.extractUserId("expired-jwt"))
                .willThrow(new CustomException(ErrorCode.ACCESS_TOKEN_EXPIRED));

        mockMvc.perform(get("/users/me")
                        .cookie(new Cookie("access_token", "expired-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCESS_TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 404를 반환한다")
    void returns404ForUnknownUser() throws Exception {
        given(jwtProvider.extractUserId("valid-jwt")).willReturn(999L);
        given(userService.getMyProfile(999L))
                .willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/users/me")
                        .cookie(new Cookie("access_token", "valid-jwt")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
}
