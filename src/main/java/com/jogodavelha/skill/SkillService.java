package com.jogodavelha.skill;

import com.jogodavelha.auth.User;
import com.jogodavelha.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SkillService {

    private final UserSkillRepository userSkillRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;

    @Transactional
    public List<SkillDTO> getInventory(UUID userId) {
        ensureInitialSkills(userId);
        return userSkillRepository.findByUser_Id(userId)
                .stream()
                .map(UserSkill::getSkill)
                .map(SkillDTO::from)
                .toList();
    }

    protected void ensureInitialSkills(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Usuário não encontrado"));

        List<Skill> initialSkills = skillRepository.findByInitialSkillTrue();
        List<UserSkill> userSkills = userSkillRepository.findByUser_Id(userId);

        List<String> userSkillCodes = userSkills.stream()
                .map(us -> us.getSkill().getCode())
                .toList();

        for (Skill skill : initialSkills) {
            if (!userSkillCodes.contains(skill.getCode())) {
                userSkillRepository.save(new UserSkill(user, skill));
            }
        }
    }
}
