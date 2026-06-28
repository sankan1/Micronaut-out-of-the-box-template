--liquibase formatted sql

--changeset sander:add-issuer-firm-schema
CREATE SCHEMA issuer_firm;
--rollback DROP SCHEMA issuer_firm CASCADE;
