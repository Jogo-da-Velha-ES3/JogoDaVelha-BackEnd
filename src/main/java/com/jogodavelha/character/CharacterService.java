package com.jogodavelha.character;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CharacterService {

    private final UserCharacterRepository userCharacterRepository;

    public List<CharacterDTO> getInventory(UUID userId) {
        return userCharacterRepository.findByUser_Id(userId)
                .stream()
                .map(UserCharacter::getCharacter)
                .map(CharacterDTO::from)
                .toList();
    }
}
