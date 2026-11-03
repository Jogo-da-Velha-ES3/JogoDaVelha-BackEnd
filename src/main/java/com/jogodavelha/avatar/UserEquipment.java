package com.jogodavelha.avatar;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "user_equipment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEquipment {
    @Id @Column(name = "user_id")
    private UUID userId;
    @Setter @Column(name = "character_id")
    private UUID characterId;
    @Setter @Column(name = "avatar_id")
    private UUID avatarId;

    public UserEquipment(UUID userId) { this.userId = userId; }
}
