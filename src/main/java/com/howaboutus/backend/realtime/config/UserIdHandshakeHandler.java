package com.howaboutus.backend.realtime.config;

import java.security.Principal;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Component
public class UserIdHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(@NonNull ServerHttpRequest request,
                                      @NonNull WebSocketHandler wsHandler,
                                      @NonNull Map<String, Object> attributes) {
        Object userId = attributes.get(WebSocketSessionAttributes.USER_ID);
        if (userId instanceof Long value) {
            return new WebSocketUserPrincipal(String.valueOf(value));
        }
        return super.determineUser(request, wsHandler, attributes);
    }
}
