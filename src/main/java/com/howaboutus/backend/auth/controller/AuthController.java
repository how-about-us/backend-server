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

    //<흐름>
    //1.프론트에서 구글로그인을 성공하고, authenticationCode를 requestBody에 담아서 넘김.
    //2.authService에서 resourceServer(google)에게 code를 넘기고 userInfo를 받아옴.
    //3. db에 유저 정보를 가져오고, 최초 가입이면, db에 유저정보 저장.
    //4. jwt token을 userid를 기준으로 만들어서 반환.
    //5. [TODO] RefreshToekn 추가 로직 만들기.
    //6. token을 담아 쿠키를 만들고, set 설정해서 response
    @PostMapping("/google/login")
    public ResponseEntity<Void> googleLogin(@RequestBody GoogleLoginRequest request) {
        String accessToken = authService.googleLogin(request.code());
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
