package com.howaboutus.backend.realtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.howaboutus.backend.auth.service.JwtProvider;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

class WebSocketHandshakeInterceptorTest {

    private JwtProvider jwtProvider;
    private WebSocketHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtProvider = mock(JwtProvider.class);
        interceptor = new WebSocketHandshakeInterceptor(jwtProvider);
    }

    @Test
    @DisplayName("handshake 쿠키의 access_token이 유효하면 userId를 session attributes에 저장한다")
    void storesUserIdFromAccessTokenCookie() {
        ServerHttpRequest request = requestWithCookie("access_token=valid-token");
        Map<String, Object> attributes = new HashMap<>();
        given(jwtProvider.extractUserId("valid-token")).willReturn(42L);

        boolean result = interceptor.beforeHandshake(
                request,
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(result).isTrue();
        assertThat(attributes).containsEntry(WebSocketSessionAttributes.USER_ID, 42L);
    }

    @Test
    @DisplayName("access_token 쿠키가 없으면 handshake는 허용하되 userId를 저장하지 않는다")
    void allowsHandshakeWithoutAccessTokenCookie() {
        ServerHttpRequest request = requestWithCookie("refresh_token=refresh-token");
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                request,
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(result).isTrue();
        assertThat(attributes).doesNotContainKey(WebSocketSessionAttributes.USER_ID);
    }

    @Test
    @DisplayName("access_token 쿠키가 유효하지 않으면 handshake는 허용하되 userId를 저장하지 않는다")
    void allowsHandshakeWithInvalidAccessTokenCookie() {
        ServerHttpRequest request = requestWithCookie("access_token=invalid-token");
        Map<String, Object> attributes = new HashMap<>();
        given(jwtProvider.extractUserId("invalid-token"))
                .willThrow(new CustomException(ErrorCode.INVALID_TOKEN));

        boolean result = interceptor.beforeHandshake(
                request,
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(result).isTrue();
        assertThat(attributes).doesNotContainKey(WebSocketSessionAttributes.USER_ID);
    }

    private ServerHttpRequest requestWithCookie(String cookieHeader) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookieHeader);
        given(request.getHeaders()).willReturn(headers);
        return request;
    }
}
