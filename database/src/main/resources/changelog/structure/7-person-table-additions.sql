--liquibase formatted sql

--changeset sander:add-person-identity-code-and-age
ALTER TABLE person.person ADD COLUMN identity_code VARCHAR(20);
ALTER TABLE person.person ADD COLUMN age INTEGER;
ALTER TABLE person.person ADD CONSTRAINT person__identity_code__unique UNIQUE (identity_code);
--rollback ALTER TABLE person.person DROP CONSTRAINT person__identity_code__unique;
--rollback ALTER TABLE person.person DROP COLUMN age;
--rollback ALTER TABLE person.person DROP COLUMN identity_code;
