package com.howaboutus.backend.common.integration.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.howaboutus.backend.common.integration.google.dto.GoogleTokenResponse;
import com.howaboutus.backend.auth.service.dto.GoogleUserInfo;
import com.howaboutus.backend.common.config.properties.GoogleOAuthProperties;
import com.howaboutus.backend.common.error.CustomException;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestBodyUriSpec;
import org.springframework.web.client.RestClient.RequestBodySpec;
import org.springframework.web.client.RestClient.ResponseSpec;
import org.springframework.web.client.RestClientException;

class GoogleOAuthClientTest {

    private RestClient restClient;
    private GoogleOAuthProperties properties;
    private GoogleOAuthClient googleOAuthClient;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        properties = new GoogleOAuthProperties(
                "test-client-id",
                "test-client-secret",
                "http://localhost:3000/callback",
                "https://oauth2.googleapis.com/token"
        );
        googleOAuthClient = new GoogleOAuthClient(restClient, properties);
    }

    @Test
    @DisplayName("Google id_token에서 사용자 정보를 추출한다")
    void extractsUserInfoFromIdToken() {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                """
                {"sub":"google-123","email":"test@gmail.com","name":"테스트","picture":"https://example.com/photo.jpg"}
                """.getBytes()
        );
        String fakeIdToken = "header." + payload + ".signature";

        GoogleTokenResponse tokenResponse = new GoogleTokenResponse("access", fakeIdToken);

        RequestBodyUriSpec requestBodyUriSpec = mock(RequestBodyUriSpec.class);
        RequestBodySpec requestBodySpec = mock(RequestBodySpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        given(restClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri(properties.tokenUri())).willReturn(requestBodySpec);
        given(requestBodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).willReturn(requestBodySpec);
        given(requestBodySpec.body(any(String.class))).willReturn(requestBodySpec);
        given(requestBodySpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.body(GoogleTokenResponse.class)).willReturn(tokenResponse);

        GoogleUserInfo userInfo = googleOAuthClient.login("auth-code");

        assertThat(userInfo.providerId()).isEqualTo("google-123");
        assertThat(userInfo.email()).isEqualTo("test@gmail.com");
        assertThat(userInfo.nickname()).isEqualTo("테스트");
        assertThat(userInfo.profileImageUrl()).isEqualTo("https://example.com/photo.jpg");
    }

    @Test
    @DisplayName("Google 토큰 교환 실패 시 CustomException을 던진다")
    void throwsExceptionWhenTokenExchangeFails() {
        RequestBodyUriSpec requestBodyUriSpec = mock(RequestBodyUriSpec.class);
        RequestBodySpec requestBodySpec = mock(RequestBodySpec.class);

        given(restClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri(properties.tokenUri())).willReturn(requestBodySpec);
        given(requestBodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).willReturn(requestBodySpec);
        given(requestBodySpec.body(any(String.class))).willReturn(requestBodySpec);
        given(requestBodySpec.retrieve()).willThrow(new RestClientException("connection refused"));

        assertThatThrownBy(() -> googleOAuthClient.login("bad-code"))
                .isInstanceOf(CustomException.class);
    }
}
