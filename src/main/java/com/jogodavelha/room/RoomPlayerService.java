package com.jogodavelha.room;

import com.jogodavelha.auth.User;
import org.springframework.stereotype.Service;

/** Fonte única dos dados dos jogadores da sala (reutilizável por eventos e snapshot). */
@Service
public class RoomPlayerService {

    public RoomPlayerInfo info(User user) {
        return new RoomPlayerInfo(user.getId(), user.getUsername(), equippedAvatar(user));
    }

    public RoomPlayerInfo host(Room room) {
        return info(room.getPlayer1());
    }

    /** Avatar equipado (cosmético do inventário, distinto do personagem do Draft).
     *  Ainda não existe fonte de dados (o perfil o expõe como null); devolve null.
     *  Quando a loja/inventário o implementar, delegar a ele aqui, em um único ponto. */
    private String equippedAvatar(User user) {
        return null;
    }
}