package com.howaboutus.backend.realtime.config;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WebSocketSessionAttributes {

    public static final String USER_ID = "userId";
    public static final String SUBSCRIBED_ROOM_IDS = "subscribedRoomIds";

    public static long requireUserId(Map<String, Object> sessionAttributes) {
        if (sessionAttributes == null) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        Object userId = sessionAttributes.get(USER_ID);
        if (!(userId instanceof Long value)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        return value;
    }

}
