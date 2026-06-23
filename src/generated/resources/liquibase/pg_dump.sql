-- Create Database Lock Table
CREATE TABLE public.databasechangeloglock (ID INTEGER NOT NULL, LOCKED BOOLEAN NOT NULL, LOCKGRANTED TIMESTAMP WITHOUT TIME ZONE, LOCKEDBY VARCHAR(255), CONSTRAINT databasechangeloglock_pkey PRIMARY KEY (ID));

-- Initialize Database Lock Table
DELETE FROM public.databasechangeloglock;

INSERT INTO public.databasechangeloglock (ID, LOCKED) VALUES (1, FALSE);

-- Lock Database
-- [redacted]

-- Create Database Change Log Table
CREATE TABLE public.databasechangelog (ID VARCHAR(255) NOT NULL, AUTHOR VARCHAR(255) NOT NULL, FILENAME VARCHAR(255) NOT NULL, DATEEXECUTED TIMESTAMP WITHOUT TIME ZONE NOT NULL, ORDEREXECUTED INTEGER NOT NULL, EXECTYPE VARCHAR(10) NOT NULL, MD5SUM VARCHAR(35), DESCRIPTION VARCHAR(255), COMMENTS VARCHAR(255), TAG VARCHAR(255), LIQUIBASE VARCHAR(20), CONTEXTS VARCHAR(255), LABELS VARCHAR(255), DEPLOYMENT_ID VARCHAR(10));

-- *********************************************************************
-- Update Database Script
-- *********************************************************************
-- Change Log: changelog/db.changelog-master.yaml
-- Ran at: 6/22/26, 1:18 PM
-- [redacted]
-- Liquibase version: 4.29.2
-- *********************************************************************

-- Changeset changelog/structure/1-person-schema.sql::add-person-schema::sander
CREATE SCHEMA person;

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-person-schema', 'sander', 'changelog/structure/1-person-schema.sql', NOW(), 1, '9:4d4937eff7a4f5ff61362e8bd729c555', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2123481997');

-- Changeset changelog/structure/2-person-table.sql::add-person-table::sander
CREATE TABLE person.person (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT person__id__pkey PRIMARY KEY (id)
);

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-person-table', 'sander', 'changelog/structure/2-person-table.sql', NOW(), 2, '9:f555528fa823edf1d4330f605159d538', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2123481997');

-- Changeset changelog/structure/3-person-table-addition.sql::add-person-table-column::sander
ALTER TABLE person.person ADD COLUMN nickname VARCHAR(255);

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-person-table-column', 'sander', 'changelog/structure/3-person-table-addition.sql', NOW(), 3, '9:a0135841a3add18f3a1465397c50ad22', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2123481997');

-- Changeset changelog/structure/4-user.sql::create-schema-system::sander
CREATE SCHEMA IF NOT EXISTS system;

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('create-schema-system', 'sander', 'changelog/structure/4-user.sql', NOW(), 4, '9:1c425d2ef647f8eced2baa7f195ed8ca', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2123481997');

-- Changeset changelog/structure/4-user.sql::create-user::sander
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

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('create-user', 'sander', 'changelog/structure/4-user.sql', NOW(), 5, '9:a7d5136b6886387b5f1498fcd2572878', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2123481997');

-- Changeset changelog/structure/4-user.sql::create-user-role::sander
CREATE TABLE system.user_role (
    user_role_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id      BIGINT NOT NULL,
    role         VARCHAR(100) NOT NULL,
    CONSTRAINT user_role__user_role_id__pkey PRIMARY KEY (user_role_id),
    CONSTRAINT user_role__user_id__fkey FOREIGN KEY (user_id)
        REFERENCES system.user (user_id) ON DELETE CASCADE,
    CONSTRAINT user_role__user_id_role__uidx UNIQUE (user_id, role)
);

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('create-user-role', 'sander', 'changelog/structure/4-user.sql', NOW(), 6, '9:fe9d0c69b10a2ba343f735f9a86b70c4', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2123481997');

-- Changeset changelog/structure/5-user-authentication.sql::create-user-authentication::sander
CREATE TABLE system.user_authentication (
    user_authentication_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id                BIGINT NOT NULL,
    auth_method            VARCHAR(20) NOT NULL,
    session_id             UUID NOT NULL,
    access_token           TEXT,
    refresh_token          TEXT,
    token_expiration       TIMESTAMP WITH TIME ZONE,
    session_expiration     TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity          TIMESTAMP WITH TIME ZONE,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT user_authentication__pkey PRIMARY KEY (user_authentication_id),
    CONSTRAINT user_authentication__user_id__fkey FOREIGN KEY (user_id)
        REFERENCES system.user (user_id) ON DELETE CASCADE,
    CONSTRAINT user_authentication__session_id__uidx UNIQUE (session_id),
    CONSTRAINT user_authentication__user_id__uidx UNIQUE (user_id)
);

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('create-user-authentication', 'sander', 'changelog/structure/5-user-authentication.sql', NOW(), 7, '9:024eb8fa2d86bb65d14a5dd188307d36', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2123481997');

-- Release Database Lock
UPDATE public.databasechangeloglock SET LOCKED = FALSE, LOCKEDBY = NULL, LOCKGRANTED = NULL WHERE ID = 1;

