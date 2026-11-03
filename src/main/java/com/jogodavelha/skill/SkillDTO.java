package com.jogodavelha.skill;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
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
