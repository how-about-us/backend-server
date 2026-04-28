package com.howaboutus.backend.realtime.config;

import java.security.Principal;

public record WebSocketUserPrincipal(String name) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
