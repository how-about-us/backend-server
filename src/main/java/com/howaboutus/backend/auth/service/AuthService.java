package com.howaboutus.backend.auth.service;

import com.howaboutus.backend.auth.entity.User;
import com.howaboutus.backend.auth.repository.UserRepository;
import com.howaboutus.backend.auth.service.dto.GoogleUserInfo;
import com.howaboutus.backend.auth.service.dto.LoginResult;
import com.howaboutus.backend.common.integration.google.GoogleOAuthClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleOAuthClient googleOAuthClient;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public LoginResult googleLogin(String authorizationCode) {
        GoogleUserInfo userInfo = googleOAuthClient.login(authorizationCode);

        User user = userRepository.findByProviderAndProviderId("GOOGLE", userInfo.providerId())
                .orElseGet(() -> userRepository.save(
                        User.ofGoogle(
                                userInfo.providerId(),
                                userInfo.email(),
                                userInfo.nickname(),
                                userInfo.profileImageUrl()
                        )
                ));

        String accessToken = jwtProvider.generateAccessToken(user.getId());
        String refreshToken = refreshTokenService.create(user.getId());

        return new LoginResult(accessToken, refreshToken, user.getId());
    }

    public LoginResult refresh(String refreshToken) {
        RefreshTokenService.RotateResult rotated = refreshTokenService.rotate(refreshToken);
        String accessToken = jwtProvider.generateAccessToken(rotated.userId());

        return new LoginResult(accessToken, rotated.token(), rotated.userId());
    }

    public void logout(String refreshToken) {
        refreshTokenService.delete(refreshToken);
    }
}
