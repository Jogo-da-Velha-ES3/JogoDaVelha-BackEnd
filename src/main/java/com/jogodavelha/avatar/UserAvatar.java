package com.jogodavelha.avatar;

import com.jogodavelha.auth.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Table(name = "user_avatars", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_avatar", columnNames = {"user_id", "avatar_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAvatar {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "avatar_id", nullable = false)
    private Avatar avatar;

    public UserAvatar(User user, Avatar avatar) {
        this.user = user;
        this.avatar = avatar;
    }
}
