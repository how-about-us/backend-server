package com.howaboutus.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.user.entity.User;
import com.howaboutus.backend.user.repository.UserRepository;
import com.howaboutus.backend.user.service.dto.UserResponse;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("존재하는 userId로 프로필을 조회한다")
    void returnsUserProfile() {
        User user = User.ofGoogle("provider-id", "test@gmail.com", "닉네임", "https://img.url/photo.jpg");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UserResponse response = userService.getMyProfile(1L);

        assertThat(response.email()).isEqualTo("test@gmail.com");
        assertThat(response.nickname()).isEqualTo("닉네임");
        assertThat(response.profileImageUrl()).isEqualTo("https://img.url/photo.jpg");
        assertThat(response.provider()).isEqualTo("GOOGLE");
    }

    @Test
    @DisplayName("존재하지 않는 userId면 USER_NOT_FOUND 예외를 던진다")
    void throwsUserNotFoundForUnknownId() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyProfile(999L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}
