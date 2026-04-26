package com.howaboutus.backend.realtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

class StompAuthenticationInterceptorTest {

    private StompAuthenticationInterceptor interceptor;
    private MessageChannel channel;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthenticationInterceptor();
        channel = mock(MessageChannel.class);
    }

    @Test
    @DisplayName("STOMP CONNECT는 session attributes에 userId가 있으면 통과한다")
    void allowsConnectWithUserId() {
        Message<byte[]> message = message(StompCommand.CONNECT, Map.of(WebSocketSessionAttributes.USER_ID, 42L));

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    @Test
    @DisplayName("STOMP CONNECT는 session attributes에 userId가 없으면 INVALID_TOKEN 예외를 던진다")
    void rejectsConnectWithoutUserId() {
        Message<byte[]> message = message(StompCommand.CONNECT, Map.of());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("CONNECT가 아닌 프레임은 인증 검사 없이 통과한다")
    void allowsNonConnectFrames() {
        Message<byte[]> message = message(StompCommand.SEND, Map.of());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    private Message<byte[]> message(StompCommand command, Map<String, Object> sessionAttributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("session-1");
        accessor.setSessionAttributes(new HashMap<>(sessionAttributes));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
