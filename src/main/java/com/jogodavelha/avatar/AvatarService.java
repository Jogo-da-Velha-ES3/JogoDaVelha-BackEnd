package com.jogodavelha.avatar;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvatarService {
    private final UserEquipmentRepository equipment;
    private final AvatarRepository avatars;

    public String equippedCode(UUID userId) {
        return equipment.findById(userId).map(UserEquipment::getAvatarId)
                .flatMap(avatars::findById).map(Avatar::getCode).orElse(Avatar.DEFAULT_CODE);
    }
}
