package com.howaboutus.backend.auth.controller;

import com.howaboutus.backend.auth.controller.dto.GoogleLoginRequest;
import com.howaboutus.backend.auth.service.AuthService;
import com.howaboutus.backend.auth.service.dto.LoginResult;
import com.howaboutus.backend.common.config.properties.JwtProperties;
import com.howaboutus.backend.common.config.properties.RefreshTokenProperties;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import java.time.Duration;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenProperties refreshTokenProperties;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @PostMapping("/google/login")
    public ResponseEntity<Void> googleLogin(@RequestBody GoogleLoginRequest request) {
        LoginResult result = authService.googleLogin(request.code());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildAccessTokenCookie(result.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(result.refreshToken()).toString())
                .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        LoginResult result = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildAccessTokenCookie(result.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(result.refreshToken()).toString())
                .build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }

        boolean secure = "prod".equals(activeProfile);
        ResponseCookie expiredAccess = ResponseCookie.from("access_token", "")
                .httpOnly(true).sameSite("Lax").path("/").maxAge(0).secure(secure).build();
        ResponseCookie expiredRefresh = ResponseCookie.from("refresh_token", "")
                .httpOnly(true).sameSite("Lax").path("/auth").maxAge(0).secure(secure).build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredAccess.toString())
                .header(HttpHeaders.SET_COOKIE, expiredRefresh.toString())
                .build();
    }

    private ResponseCookie buildAccessTokenCookie(String token) {
        boolean secure = "prod".equals(activeProfile);
        return ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMillis(jwtProperties.accessTokenExpiration()))
                .secure(secure)
                .build();
    }

    private ResponseCookie buildRefreshTokenCookie(String token) {
        boolean secure = "prod".equals(activeProfile);
        return ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/auth")
                .maxAge(Duration.ofMillis(refreshTokenProperties.expiration()))
                .secure(secure)
                .build();
    }
}
