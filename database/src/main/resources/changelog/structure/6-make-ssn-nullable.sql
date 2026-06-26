--liquibase formatted sql

--changeset sander:make-ssn-nullable
ALTER TABLE system.user ALTER COLUMN ssn DROP NOT NULL;
--rollback ALTER TABLE system.user ALTER COLUMN ssn SET NOT NULL;
