package com.jogodavelha.room;

import com.jogodavelha.auth.User;
import com.jogodavelha.avatar.AvatarService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Fonte única dos dados dos jogadores da sala (reutilizável por eventos e snapshot). */
@Service
@RequiredArgsConstructor
public class RoomPlayerService {
    private final AvatarService avatars;

    public RoomPlayerInfo info(User user) {
        return new RoomPlayerInfo(user.getId(), user.getUsername(), avatars.equippedCode(user.getId()));
    }

    public RoomPlayerInfo host(Room room) {
        return info(room.getPlayer1());
    }

}
