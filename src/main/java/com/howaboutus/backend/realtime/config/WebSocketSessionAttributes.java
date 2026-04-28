package com.howaboutus.backend.realtime.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WebSocketSessionAttributes {

    public static final String USER_ID = "userId";
    public static final String SUBSCRIBED_ROOM_IDS = "subscribedRoomIds";

}
