--liquibase formatted sql

--changeset sander:add-insurance-schema
CREATE SCHEMA insurance;
--rollback DROP SCHEMA insurance CASCADE;
