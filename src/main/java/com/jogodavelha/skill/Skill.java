package com.jogodavelha.skill;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "skills")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "effect_category", nullable = false)
    private SkillCategory effectCategory;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "initial_skill")
    private Boolean initialSkill;

    public Skill(
            String name,
            SkillCategory effectCategory,
            String description,
            Boolean initialSkill
    ) {
        this.name = name;
        this.effectCategory = effectCategory;
        this.description = description;
        this.initialSkill = initialSkill;
    }
}
