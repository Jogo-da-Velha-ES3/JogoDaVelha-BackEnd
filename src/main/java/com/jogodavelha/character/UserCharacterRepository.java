package com.jogodavelha.character;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserCharacterRepository extends JpaRepository<UserCharacter, UUID> {

    List<UserCharacter> findByUser_Id(UUID userId);

}