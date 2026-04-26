package com.howaboutus.backend.realtime.config;

import com.howaboutus.backend.auth.service.JwtProvider;
import com.howaboutus.backend.common.error.CustomException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
@RequiredArgsConstructor
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final JwtProvider jwtProvider;

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request,
                                   @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler,
                                   @NonNull Map<String, Object> attributes) {
        return extractAccessToken(request)
                .map(token -> storeUserId(token, attributes))
                .orElse(false);
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request,
                               @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler,
                               Exception exception) {
    }

    private Optional<String> extractAccessToken(ServerHttpRequest request) {
        return request.getHeaders()
                .getOrEmpty(HttpHeaders.COOKIE)
                .stream()
                .flatMap(header -> Arrays.stream(header.split(";")))
                .map(String::trim)
                .filter(cookie -> cookie.startsWith(ACCESS_TOKEN_COOKIE + "="))
                .map(cookie -> cookie.substring((ACCESS_TOKEN_COOKIE + "=").length()))
                .filter(token -> !token.isBlank())
                .findFirst();
    }

    private boolean storeUserId(String token, Map<String, Object> attributes) {
        try {
            attributes.put(WebSocketSessionAttributes.USER_ID, jwtProvider.extractUserId(token));
            return true;
        } catch (CustomException ignored) {
            attributes.remove(WebSocketSessionAttributes.USER_ID);
            return false;
        }
    }
}
