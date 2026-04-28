package com.howaboutus.backend.realtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;

class UserIdHandshakeHandlerTest {

    @Test
    @DisplayName("session attributes의 userId를 Principal name으로 사용한다")
    void determinesUserFromSessionUserId() {
        UserIdHandshakeHandler handler = new UserIdHandshakeHandler();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WebSocketSessionAttributes.USER_ID, 42L);

        Principal principal = handler.determineUser(
                mock(ServerHttpRequest.class),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(principal).isNotNull();
        assertThat(principal.getName()).isEqualTo("42");
    }
}
