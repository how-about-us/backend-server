package com.howaboutus.backend.rooms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.howaboutus.backend.auth.entity.User;
import com.howaboutus.backend.auth.repository.UserRepository;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import com.howaboutus.backend.rooms.entity.Room;
import com.howaboutus.backend.rooms.entity.RoomMember;
import com.howaboutus.backend.rooms.entity.RoomRole;
import com.howaboutus.backend.rooms.repository.RoomMemberRepository;
import com.howaboutus.backend.rooms.repository.RoomRepository;
import com.howaboutus.backend.rooms.service.dto.RoomCreateCommand;
import com.howaboutus.backend.rooms.service.dto.RoomDetailResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock private RoomRepository roomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private InviteCodeGenerator inviteCodeGenerator;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(roomRepository, roomMemberRepository,
                userRepository, inviteCodeGenerator);
    }

    @Test
    @DisplayName("방 생성 시 Room과 HOST RoomMember를 저장하고 결과를 반환한다")
    void createRoomSavesRoomAndHostMember() {
        Long userId = 1L;
        User user = User.ofGoogle("google-id", "test@test.com", "테스터", null);
        ReflectionTestUtils.setField(user, "id", userId);

        Room savedRoom = Room.create("부산 여행", "부산",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3),
                "aB3xK9mQ2w", userId);
        ReflectionTestUtils.setField(savedRoom, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(savedRoom, "createdAt", Instant.now());

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(inviteCodeGenerator.generate()).willReturn("aB3xK9mQ2w");
        given(roomRepository.save(any(Room.class))).willReturn(savedRoom);

        RoomCreateCommand command = new RoomCreateCommand("부산 여행", "부산",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3));

        RoomDetailResult result = roomService.create(command, userId);

        assertThat(result.title()).isEqualTo("부산 여행");
        assertThat(result.inviteCode()).isEqualTo("aB3xK9mQ2w");
        assertThat(result.role()).isEqualTo(RoomRole.HOST);
        assertThat(result.memberCount()).isEqualTo(1);

        ArgumentCaptor<RoomMember> memberCaptor = ArgumentCaptor.forClass(RoomMember.class);
        verify(roomMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getRole()).isEqualTo(RoomRole.HOST);
    }

    @Test
    @DisplayName("방 생성 시 startDate > endDate면 INVALID_DATE_RANGE 예외")
    void createRoomThrowsWhenStartDateAfterEndDate() {
        RoomCreateCommand command = new RoomCreateCommand("부산 여행", "부산",
                LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 3));

        assertThatThrownBy(() -> roomService.create(command, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_DATE_RANGE);
    }
}
