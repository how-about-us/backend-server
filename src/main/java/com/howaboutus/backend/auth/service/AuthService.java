package com.howaboutus.backend.auth.service;

import com.howaboutus.backend.auth.entity.User;
import com.howaboutus.backend.auth.repository.UserRepository;
import com.howaboutus.backend.auth.service.dto.GoogleUserInfo;
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

    @Transactional
    public String googleLogin(String authorizationCode) {
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

        return jwtProvider.generateAccessToken(user.getId());
    }
}
