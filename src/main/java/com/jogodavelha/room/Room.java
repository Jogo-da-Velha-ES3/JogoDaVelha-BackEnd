package com.jogodavelha.room;

import com.jogodavelha.auth.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 4)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RoomStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player1_id", nullable = false)
    private User player1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player2_id")
    private User player2;

    public Room(String code, User player1) {
        if (code == null || !code.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("O código deve conter quatro dígitos.");
        }
        if (player1 == null || player1.getId() == null) {
            throw new IllegalArgumentException("O primeiro jogador deve possuir um ID.");
        }
        this.code = code;
        this.player1 = player1;
        this.status = RoomStatus.WAITING;
    }
}
