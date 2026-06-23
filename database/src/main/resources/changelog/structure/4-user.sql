--liquibase formatted sql

--changeset sander:create-schema-system
CREATE SCHEMA IF NOT EXISTS system;
--rollback DROP SCHEMA IF EXISTS system;

--changeset sander:create-user
CREATE TABLE system.user (
    user_id     BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    uuid        UUID NOT NULL,
    ssn         VARCHAR(255) NOT NULL,
    email       VARCHAR(255),
    first_name  VARCHAR(200) NOT NULL,
    last_name   VARCHAR(200) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT user__user_id__pkey PRIMARY KEY (user_id),
    CONSTRAINT user__uuid__uidx UNIQUE (uuid),
    CONSTRAINT user__ssn__uidx UNIQUE (ssn)
);
--rollback DROP TABLE system.user;

--changeset sander:create-user-role
CREATE TABLE system.user_role (
    user_role_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id      BIGINT NOT NULL,
    role         VARCHAR(100) NOT NULL,
    CONSTRAINT user_role__user_role_id__pkey PRIMARY KEY (user_role_id),
    CONSTRAINT user_role__user_id__fkey FOREIGN KEY (user_id)
        REFERENCES system.user (user_id) ON DELETE CASCADE,
    CONSTRAINT user_role__user_id_role__uidx UNIQUE (user_id, role)
);
--rollback DROP TABLE system.user_role;
