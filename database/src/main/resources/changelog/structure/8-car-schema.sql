--liquibase formatted sql

--changeset sander:add-car-schema
CREATE SCHEMA car;
--rollback DROP SCHEMA car CASCADE;
