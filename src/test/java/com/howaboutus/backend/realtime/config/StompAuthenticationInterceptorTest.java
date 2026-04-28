package com.howaboutus.backend.realtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

@ExtendWith(MockitoExtension.class)
class StompAuthenticationInterceptorTest {

    @InjectMocks
    private StompAuthenticationInterceptor interceptor;

    @Mock
    private MessageChannel channel;

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
    @DisplayName("STOMP CONNECT는 userId가 Long이 아닌 타입이면 INVALID_TOKEN 예외를 던진다")
    void rejectsConnectWhenUserIdIsNotLong() {
        Message<byte[]> message = message(StompCommand.CONNECT, Map.of(WebSocketSessionAttributes.USER_ID, "not-a-long"));

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
