package com.jogodavelha.skill;

import java.util.UUID;

public record SkillDTO(
        UUID id,
        String name,
        String code,
        String description,
        Boolean initialSkill,
        Boolean offensive,
        Integer priceCoins
) {

    public static SkillDTO from(Skill skill) {
        return new SkillDTO(
                skill.getId(),
                skill.getName(),
                skill.getCode(),
                skill.getDescription(),
                skill.getInitialSkill(),
                skill.getOffensive(),
                skill.getPriceCoins()
        );
    }
}
