package com.jogodavelha.skill;

import com.jogodavelha.auth.User;
import com.jogodavelha.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    @Mock
    private UserSkillRepository userSkillRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SkillService service;

    @Test
    void returnsUserInventory() {

        UUID userId = UUID.randomUUID();

        Skill skill =
                new Skill(
                        "Bomba",
                        "BOMBA",
                        SkillCategory.REMOVAL,
                        "Remove uma peça do oponente.",
                        true,
                        true,
                        0
                );

        User user =
                new User(
                        "player",
                        "hash",
                        "player@test.com"
                );
        user.setId(userId);

        UserSkill ownership =
                new UserSkill(user, skill);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(skillRepository.findByInitialSkillTrue()).thenReturn(List.of());
        when(userSkillRepository.findByUser_Id(userId))
                .thenReturn(List.of(ownership));

        List<SkillDTO> inventory =
                service.getInventory(userId);

        assertEquals(1, inventory.size());

        assertEquals(
                "BOMBA",
                inventory.get(0).code()
        );
    }

    @Test
    void addsInitialSkillsWhenInventoryIsEmpty() {

        UUID userId = UUID.randomUUID();

        Skill initialSkill =
                new Skill(
                        "Bomba",
                        "BOMBA",
                        SkillCategory.REMOVAL,
                        "Remove uma peça do oponente.",
                        true,
                        true,
                        0
                );

        User user =
                new User(
                        "player",
                        "hash",
                        "player@test.com"
                );
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(skillRepository.findByInitialSkillTrue()).thenReturn(List.of(initialSkill));
        when(userSkillRepository.findByUser_Id(userId))
                .thenReturn(List.of())
                .thenReturn(List.of(new UserSkill(user, initialSkill)));
        when(userSkillRepository.save(any(UserSkill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<SkillDTO> inventory = service.getInventory(userId);

        assertEquals(1, inventory.size());
        assertEquals("BOMBA", inventory.get(0).code());
        verify(userSkillRepository).save(any(UserSkill.class));
    }

    @Test
    void doesNotDuplicateExistingInitialSkills() {

        UUID userId = UUID.randomUUID();

        Skill initialSkill =
                new Skill(
                        "Bomba",
                        "BOMBA",
                        SkillCategory.REMOVAL,
                        "Remove uma peça do oponente.",
                        true,
                        true,
                        0
                );

        User user =
                new User(
                        "player",
                        "hash",
                        "player@test.com"
                );
        user.setId(userId);

        UserSkill existingOwnership = new UserSkill(user, initialSkill);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(skillRepository.findByInitialSkillTrue()).thenReturn(List.of(initialSkill));
        when(userSkillRepository.findByUser_Id(userId)).thenReturn(List.of(existingOwnership));

        List<SkillDTO> inventory = service.getInventory(userId);

        assertEquals(1, inventory.size());
        assertEquals("BOMBA", inventory.get(0).code());
        verify(userSkillRepository, never()).save(any(UserSkill.class));
    }

    @Test
    void addsMissingInitialSkillsWhileKeepingExistingOnes() {

        UUID userId = UUID.randomUUID();

        Skill initialSkill1 =
                new Skill(
                        "Bomba",
                        "BOMBA",
                        SkillCategory.REMOVAL,
                        "Remove uma peça do oponente.",
                        true,
                        true,
                        0
                );

        Skill initialSkill2 =
                new Skill(
                        "Escudo",
                        "ESCUDO",
                        SkillCategory.DEFENSE,
                        "Protege uma peça.",
                        true,
                        false,
                        0
                );

        User user =
                new User(
                        "player",
                        "hash",
                        "player@test.com"
                );
        user.setId(userId);

        UserSkill existingOwnership = new UserSkill(user, initialSkill1);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(skillRepository.findByInitialSkillTrue()).thenReturn(List.of(initialSkill1, initialSkill2));
        when(userSkillRepository.findByUser_Id(userId))
                .thenReturn(List.of(existingOwnership));

        List<SkillDTO> inventory = service.getInventory(userId);

        assertEquals(1, inventory.size());
        assertEquals("BOMBA", inventory.get(0).code());
    }
}