package com.howaboutus.backend.realtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;

@ExtendWith(MockitoExtension.class)
class UserIdHandshakeHandlerTest {

    @Mock
    private ServerHttpRequest mockRequest;

    @Mock
    private WebSocketHandler mockHandler;

    @Test
    @DisplayName("session attributes의 userId를 Principal name으로 사용한다")
    void determinesUserFromSessionUserId() {
        UserIdHandshakeHandler handler = new UserIdHandshakeHandler();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WebSocketSessionAttributes.USER_ID, 42L);

        Principal principal = handler.determineUser(mockRequest, mockHandler, attributes);

        assertThat(principal).isNotNull();
        assertThat(principal.getName()).isEqualTo("42");
    }
}
