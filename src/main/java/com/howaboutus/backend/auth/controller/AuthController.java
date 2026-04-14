package com.howaboutus.backend.auth.controller;

import com.howaboutus.backend.auth.controller.dto.GoogleLoginRequest;
import com.howaboutus.backend.auth.service.AuthService;
import java.time.Duration;

import com.howaboutus.backend.common.config.properties.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/google/login")
    public ResponseEntity<Void> googleLogin(@RequestBody GoogleLoginRequest request) {
        String accessToken = authService.googleLogin(request.code());
        //구글로그인 시도시, 요청 헤더에, Authentication code를 받아와서, google에 로그인요청을 목척으로
        ResponseCookie cookie = ResponseCookie.from("access_token", accessToken)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMillis(jwtProperties.accessTokenExpiration()))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }
}
