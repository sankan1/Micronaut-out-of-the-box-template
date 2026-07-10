--liquibase formatted sql

--changeset sander:add-document-schema
CREATE SCHEMA document;
--rollback DROP SCHEMA document CASCADE;
