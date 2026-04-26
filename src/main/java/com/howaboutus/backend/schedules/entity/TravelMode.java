package com.howaboutus.backend.schedules.entity;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import java.util.Locale;

public enum TravelMode {
    DRIVING,
    WALKING,
    BICYCLING,
    TRANSIT;

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_TRAVEL_MODE);
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return TravelMode.valueOf(normalized).name();
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_TRAVEL_MODE, e);
        }
    }

    public static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        return normalize(value);
    }

    public static String normalizeOrDefault(String value) {
        if (value == null) {
            return DRIVING.name();
        }
        return normalize(value);
    }

    public static boolean isAllowedOrNull(String value) {
        try {
            normalizeOrDefault(value);
            return true;
        } catch (CustomException e) {
            return false;
        }
    }
}
