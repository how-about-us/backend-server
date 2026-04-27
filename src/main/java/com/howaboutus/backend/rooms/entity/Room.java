package com.howaboutus.backend.rooms.entity;

import com.howaboutus.backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseTimeEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 200)
    private String destination;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "invite_code", nullable = false, unique = true, length = 50)
    private String inviteCode;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    private Room(String title, String destination, LocalDate startDate,
                 LocalDate endDate, String inviteCode, Long createdBy) {
        this.title = title;
        this.destination = destination;
        this.startDate = startDate;
        this.endDate = endDate;
        this.inviteCode = inviteCode;
        this.createdBy = createdBy;
    }

    public static Room create(String title, String destination, LocalDate startDate,
                              LocalDate endDate, String inviteCode, Long createdBy) {
        return new Room(title, destination, startDate, endDate, inviteCode, createdBy);
    }

    public void update(String title, String destination, LocalDate startDate, LocalDate endDate) {
        if (title != null) {
            this.title = title;
        }
        if (destination != null) {
            this.destination = destination;
        }
        if (startDate != null) {
            this.startDate = startDate;
        }
        if (endDate != null) {
            this.endDate = endDate;
        }
    }
    public void regenerateInviteCode(String newCode) {
        this.inviteCode = newCode;
    }
}
