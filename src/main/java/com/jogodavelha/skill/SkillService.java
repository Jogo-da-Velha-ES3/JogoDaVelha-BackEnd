package com.jogodavelha.skill;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkillService {

    private final UserSkillRepository userSkillRepository;

    public List<SkillDTO> getInventory(UUID userId) {
        return userSkillRepository.findByUser_Id(userId)
                .stream()
                .map(UserSkill::getSkill)
                .map(SkillDTO::from)
                .toList();
    }

    public List<SkillDTO> getEligibleInitialSkills(UUID userId) {
        return userSkillRepository.findByUser_Id(userId)
                .stream()
                .map(UserSkill::getSkill)
                .filter(skill -> Boolean.TRUE.equals(skill.getInitialSkill()))
                .map(SkillDTO::from)
                .toList();
    }
}
