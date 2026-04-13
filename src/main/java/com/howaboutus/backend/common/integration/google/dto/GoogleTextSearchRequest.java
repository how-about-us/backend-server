package com.howaboutus.backend.common.integration.google.dto;

public record GoogleTextSearchRequest(String textQuery, String languageCode) {

    public static GoogleTextSearchRequest withKorean(String textQuery) {
        return new GoogleTextSearchRequest(textQuery, "ko");
    }
}
