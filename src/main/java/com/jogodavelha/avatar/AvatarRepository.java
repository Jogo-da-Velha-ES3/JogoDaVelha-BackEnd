package com.jogodavelha.avatar;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AvatarRepository extends JpaRepository<Avatar, UUID> { }
