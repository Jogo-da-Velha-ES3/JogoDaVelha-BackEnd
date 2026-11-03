package com.jogodavelha.avatar;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface UserEquipmentRepository extends JpaRepository<UserEquipment, UUID> { }
