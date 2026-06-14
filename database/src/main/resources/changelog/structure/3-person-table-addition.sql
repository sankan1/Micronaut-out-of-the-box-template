--liquibase formatted sql

--changeset sander:add-person-table-column
ALTER TABLE person.person ADD COLUMN nickname VARCHAR(255);
--rollback ALTER TABLE person.person DROP COLUMN nickname;