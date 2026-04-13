package com.howaboutus.backend.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.howaboutus.backend.auth.entity.User;
import com.howaboutus.backend.support.BaseIntegrationTest;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("provider와 providerId로 사용자를 조회한다")
    void findsByProviderAndProviderId() {
        User user = User.ofGoogle("google-123", "test@gmail.com", "테스트", "https://example.com/photo.jpg");
        userRepository.save(user);

        Optional<User> found = userRepository.findByProviderAndProviderId("GOOGLE", "google-123");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@gmail.com");
    }

    @Test
    @DisplayName("존재하지 않는 provider/providerId 조회 시 빈 Optional을 반환한다")
    void returnsEmptyWhenNotFound() {
        Optional<User> found = userRepository.findByProviderAndProviderId("GOOGLE", "nonexistent");

        assertThat(found).isEmpty();
    }
}
