package com.jogodavelha.avatar;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, UUID> {
    List<UserAvatar> findByUser_Id(UUID userId);
}
