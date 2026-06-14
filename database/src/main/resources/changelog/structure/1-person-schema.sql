--liquibase formatted sql

--changeset sander:add-person-schema
CREATE SCHEMA person;
--rollback DROP SCHEMA person CASCADE;