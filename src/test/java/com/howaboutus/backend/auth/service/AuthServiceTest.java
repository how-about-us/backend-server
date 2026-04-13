package com.howaboutus.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.howaboutus.backend.auth.entity.User;
import com.howaboutus.backend.auth.repository.UserRepository;
import com.howaboutus.backend.auth.service.dto.GoogleUserInfo;
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

    @BeforeEach
    void setUp() {
        googleOAuthClient = Mockito.mock(GoogleOAuthClient.class);
        userRepository = Mockito.mock(UserRepository.class);
        jwtProvider = Mockito.mock(JwtProvider.class);
        authService = new AuthService(googleOAuthClient, userRepository, jwtProvider);
    }

    @Test
    @DisplayName("신규 사용자 로그인 시 회원가입 후 JWT를 발급한다")
    void registersNewUserAndReturnsJwt() {
        GoogleUserInfo userInfo = new GoogleUserInfo("google-123", "test@gmail.com", "테스트", null);
        given(googleOAuthClient.login("auth-code")).willReturn(userInfo);
        given(userRepository.findByProviderAndProviderId("GOOGLE", "google-123")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(jwtProvider.generateAccessToken(any())).willReturn("jwt-token");

        String token = authService.googleLogin("auth-code");

        assertThat(token).isEqualTo("jwt-token");
    }

    @Test
    @DisplayName("기존 사용자 로그인 시 조회 후 JWT를 발급한다")
    void returnsJwtForExistingUser() {
        GoogleUserInfo userInfo = new GoogleUserInfo("google-123", "test@gmail.com", "테스트", null);
        User existingUser = User.ofGoogle("google-123", "test@gmail.com", "테스트", null);

        given(googleOAuthClient.login("auth-code")).willReturn(userInfo);
        given(userRepository.findByProviderAndProviderId("GOOGLE", "google-123"))
                .willReturn(Optional.of(existingUser));
        given(jwtProvider.generateAccessToken(any())).willReturn("jwt-token");

        String token = authService.googleLogin("auth-code");

        assertThat(token).isEqualTo("jwt-token");
    }
}
