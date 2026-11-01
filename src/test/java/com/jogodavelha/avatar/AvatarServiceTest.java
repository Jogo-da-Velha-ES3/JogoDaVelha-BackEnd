package com.jogodavelha.avatar;

import com.jogodavelha.auth.User;
import com.jogodavelha.room.RoomPlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AvatarServiceTest {
    private final UserEquipmentRepository equipment = mock(UserEquipmentRepository.class);
    private final AvatarRepository avatars = mock(AvatarRepository.class);
    private final AvatarService service = new AvatarService(equipment, avatars);

    @Test
    void missingEquipmentUsesDefaultForTheRoomPlayer() {
        User user = new User("ana", "hash", "ana@example.com");
        user.setId(UUID.randomUUID());

        var info = new RoomPlayerService(service).info(user);

        assertEquals(user.getId(), info.id());
        assertEquals("ana", info.username());
        assertEquals(Avatar.DEFAULT_CODE, info.avatar());
        verifyNoInteractions(avatars);
    }

    @Test
    void equippedAvatarIsReadFromThePersistedEquipment() {
        UUID userId = UUID.randomUUID(), avatarId = UUID.randomUUID();
        var selected = new UserEquipment(userId);
        selected.setAvatarId(avatarId);
        Avatar avatar = new Avatar("TEST_AVATAR", "Avatar", "", 10);
        ReflectionTestUtils.setField(avatar, "id", avatarId);
        when(equipment.findById(userId)).thenReturn(Optional.of(selected));
        when(avatars.findById(avatarId)).thenReturn(Optional.of(avatar));

        assertEquals("TEST_AVATAR", service.equippedCode(userId));
    }
}
