package com.howaboutus.backend.common.integration.google;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.howaboutus.backend.common.integration.google.dto.GoogleTokenResponse;
import com.howaboutus.backend.auth.service.dto.GoogleUserInfo;
import com.howaboutus.backend.common.config.properties.GoogleOAuthProperties;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class GoogleOAuthClient {

    private final RestClient googleOAuthRestClient;
    private final GoogleOAuthProperties properties;
    private final ObjectMapper objectMapper;

    public GoogleUserInfo login(String authorizationCode) {
        GoogleTokenResponse tokenResponse = exchangeCode(authorizationCode);
        return extractUserInfo(tokenResponse.idToken());
    }

    private GoogleTokenResponse exchangeCode(String code) {
        String body = "grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&client_id=" + URLEncoder.encode(properties.clientId(), StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(properties.clientSecret(), StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(properties.redirectUri(), StandardCharsets.UTF_8);

        try {
            return googleOAuthRestClient.post()
                    .uri(properties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(GoogleTokenResponse.class);
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }

    private GoogleUserInfo extractUserInfo(String idToken) {
        try {
            String payload = idToken.split("\\.")[1];
            byte[] decoded = Base64.getUrlDecoder().decode(payload);
            JsonNode claims = objectMapper.readTree(decoded);
            String profileImageUrl = null;
            if (claims.has("picture")) {
                profileImageUrl = claims.get("picture").asString();
            }
            return new GoogleUserInfo(
                    claims.get("sub").asString(),
                    claims.get("email").asString(),
                    claims.get("name").asString(),
                    profileImageUrl
            );
        } catch (Exception e) {
            throw new CustomException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }
}
