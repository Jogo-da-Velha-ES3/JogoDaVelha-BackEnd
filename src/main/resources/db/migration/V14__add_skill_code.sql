ALTER TABLE skills
    ADD COLUMN code VARCHAR(50);

UPDATE skills
SET code = REPLACE(name, ' ', '_');

ALTER TABLE skills
    ALTER COLUMN code SET NOT NULL,
    ADD CONSTRAINT uk_skills_code UNIQUE (code),
    ADD CONSTRAINT chk_skills_code_format
        CHECK (code ~ '^[A-Z]+(_[A-Z]+)*$');
