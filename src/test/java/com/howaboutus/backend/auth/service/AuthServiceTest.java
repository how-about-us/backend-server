package com.howaboutus.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.auth.entity.User;
import com.howaboutus.backend.auth.repository.UserRepository;
import com.howaboutus.backend.auth.service.dto.GoogleUserInfo;
import com.howaboutus.backend.auth.service.dto.LoginResult;
import com.howaboutus.backend.auth.service.dto.RotateResult;
import java.util.Optional;

import com.howaboutus.backend.common.integration.google.GoogleOAuthClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AuthServiceTest {

    private AuthService authService;
    private GoogleOAuthClient googleOAuthClient;
    private UserRepository userRepository;
    private JwtProvider jwtProvider;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        googleOAuthClient = Mockito.mock(GoogleOAuthClient.class);
        userRepository = Mockito.mock(UserRepository.class);
        jwtProvider = Mockito.mock(JwtProvider.class);
        refreshTokenService = Mockito.mock(RefreshTokenService.class);
        authService = new AuthService(googleOAuthClient, userRepository, jwtProvider, refreshTokenService);
    }

    @Test
    @DisplayName("신규 사용자 로그인 시 회원가입 후 Access Token과 Refresh Token을 발급한다")
    void registersNewUserAndReturnsTokens() {
        GoogleUserInfo userInfo = new GoogleUserInfo("google-123", "test@gmail.com", "테스트", null);
        given(googleOAuthClient.login("auth-code")).willReturn(userInfo);
        given(userRepository.findByProviderAndProviderId("GOOGLE", "google-123")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(jwtProvider.generateAccessToken(any())).willReturn("jwt-token");
        given(refreshTokenService.create(any())).willReturn("1:refresh-uuid");

        LoginResult result = authService.googleLogin("auth-code");

        assertThat(result.accessToken()).isEqualTo("jwt-token");
        assertThat(result.refreshToken()).isEqualTo("1:refresh-uuid");
    }

    @Test
    @DisplayName("기존 사용자 로그인 시 조회 후 Access Token과 Refresh Token을 발급한다")
    void returnsTokensForExistingUser() {
        GoogleUserInfo userInfo = new GoogleUserInfo("google-123", "test@gmail.com", "테스트", null);
        User existingUser = User.ofGoogle("google-123", "test@gmail.com", "테스트", null);

        given(googleOAuthClient.login("auth-code")).willReturn(userInfo);
        given(userRepository.findByProviderAndProviderId("GOOGLE", "google-123"))
                .willReturn(Optional.of(existingUser));
        given(jwtProvider.generateAccessToken(any())).willReturn("jwt-token");
        given(refreshTokenService.create(any())).willReturn("1:refresh-uuid");

        LoginResult result = authService.googleLogin("auth-code");

        assertThat(result.accessToken()).isEqualTo("jwt-token");
        assertThat(result.refreshToken()).isEqualTo("1:refresh-uuid");
    }

    @Test
    @DisplayName("Refresh Token으로 새 토큰 쌍을 발급한다")
    void refreshReturnsNewTokens() {
        given(refreshTokenService.rotate("1:old-uuid"))
                .willReturn(new RotateResult("1:new-uuid", 1L));
        given(jwtProvider.generateAccessToken(1L)).willReturn("new-jwt");

        LoginResult result = authService.refresh("1:old-uuid");

        assertThat(result.accessToken()).isEqualTo("new-jwt");
        assertThat(result.refreshToken()).isEqualTo("1:new-uuid");
    }

    @Test
    @DisplayName("로그아웃 시 RefreshTokenService.delete를 호출한다")
    void logoutDeletesToken() {
        authService.logout("1:some-uuid");

        verify(refreshTokenService).delete("1:some-uuid");
    }
}
