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
-- Ran at: 6/28/26, 4:46 PM
-- [redacted]
-- Liquibase version: 4.29.2
-- *********************************************************************

-- Changeset changelog/structure/1-person-schema.sql::add-person-schema::sander
CREATE SCHEMA person;

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-person-schema', 'sander', 'changelog/structure/1-person-schema.sql', NOW(), 1, '9:4d4937eff7a4f5ff61362e8bd729c555', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

-- Changeset changelog/structure/2-person-table.sql::add-person-table::sander
CREATE TABLE person.person (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT person__id__pkey PRIMARY KEY (id)
);

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-person-table', 'sander', 'changelog/structure/2-person-table.sql', NOW(), 2, '9:f555528fa823edf1d4330f605159d538', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

-- Changeset changelog/structure/3-person-table-addition.sql::add-person-table-column::sander
ALTER TABLE person.person ADD COLUMN nickname VARCHAR(255);

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-person-table-column', 'sander', 'changelog/structure/3-person-table-addition.sql', NOW(), 3, '9:a0135841a3add18f3a1465397c50ad22', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

-- Changeset changelog/structure/4-user.sql::create-schema-system::sander
CREATE SCHEMA IF NOT EXISTS system;

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('create-schema-system', 'sander', 'changelog/structure/4-user.sql', NOW(), 4, '9:1c425d2ef647f8eced2baa7f195ed8ca', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

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

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('create-user', 'sander', 'changelog/structure/4-user.sql', NOW(), 5, '9:a7d5136b6886387b5f1498fcd2572878', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

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

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('create-user-role', 'sander', 'changelog/structure/4-user.sql', NOW(), 6, '9:fe9d0c69b10a2ba343f735f9a86b70c4', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

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

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('create-user-authentication', 'sander', 'changelog/structure/5-user-authentication.sql', NOW(), 7, '9:024eb8fa2d86bb65d14a5dd188307d36', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

-- Changeset changelog/structure/6-make-ssn-nullable.sql::make-ssn-nullable::sander
ALTER TABLE system.user ALTER COLUMN ssn DROP NOT NULL;

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('make-ssn-nullable', 'sander', 'changelog/structure/6-make-ssn-nullable.sql', NOW(), 8, '9:6b669defaede961f06724e2b04aae7da', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

-- Changeset changelog/structure/7-person-table-additions.sql::add-person-identity-code-and-age::sander
ALTER TABLE person.person ADD COLUMN identity_code VARCHAR(20);

ALTER TABLE person.person ADD COLUMN age INTEGER;

ALTER TABLE person.person ADD CONSTRAINT person__identity_code__unique UNIQUE (identity_code);

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-person-identity-code-and-age', 'sander', 'changelog/structure/7-person-table-additions.sql', NOW(), 9, '9:66f2f66226ddc1eefa4e6cf50ff5435d', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

-- Changeset changelog/structure/8-car-schema.sql::add-car-schema::sander
CREATE SCHEMA car;

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-car-schema', 'sander', 'changelog/structure/8-car-schema.sql', NOW(), 10, '9:cfc85b929a9a124528a0e9f3dbda2fdb', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

-- Changeset changelog/structure/9-car-table.sql::add-car-table::sander
CREATE TABLE car.car (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    owner_id BIGINT,
    mark VARCHAR(255) NOT NULL,
    model VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT car__id__pkey PRIMARY KEY (id),
    CONSTRAINT car__owner_id__fkey FOREIGN KEY (owner_id) REFERENCES person.person (id)
);

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-car-table', 'sander', 'changelog/structure/9-car-table.sql', NOW(), 11, '9:618480dbeeb2f94963baa5a34176e05c', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

-- Changeset changelog/structure/10-issuer-firm-schema.sql::add-issuer-firm-schema::sander
CREATE SCHEMA issuer_firm;

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-issuer-firm-schema', 'sander', 'changelog/structure/10-issuer-firm-schema.sql', NOW(), 12, '9:f11a394cfe8b2970ce5df196ff8bea7c', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

-- Changeset changelog/structure/11-issuer-firm-table.sql::add-issuer-firm-table::sander
CREATE TABLE issuer_firm.issuer_firm (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    car_id BIGINT NOT NULL,
    firm_name VARCHAR(255) NOT NULL,
    CONSTRAINT issuer_firm__id__pkey PRIMARY KEY (id),
    CONSTRAINT issuer_firm__car_id__fkey FOREIGN KEY (car_id) REFERENCES car.car (id)
);

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-issuer-firm-table', 'sander', 'changelog/structure/11-issuer-firm-table.sql', NOW(), 13, '9:9d80b9c00d9776f09d61575fc38f3509', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

-- Changeset changelog/structure/12-insurance-schema.sql::add-insurance-schema::sander
CREATE SCHEMA insurance;

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-insurance-schema', 'sander', 'changelog/structure/12-insurance-schema.sql', NOW(), 14, '9:16f3292cf939051cb32d04c86acf1074', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

-- Changeset changelog/structure/13-insurance-table.sql::add-insurance-table::sander
CREATE TABLE insurance.insurance (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    person_id BIGINT NOT NULL,
    car_id BIGINT NOT NULL,
    insurer_name VARCHAR(255) NOT NULL,
    plan VARCHAR(100) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    expiry_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT insurance__id__pkey PRIMARY KEY (id),
    CONSTRAINT insurance__person_id__fkey FOREIGN KEY (person_id) REFERENCES person.person (id),
    CONSTRAINT insurance__car_id__fkey FOREIGN KEY (car_id) REFERENCES car.car (id)
);

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-insurance-table', 'sander', 'changelog/structure/13-insurance-table.sql', NOW(), 15, '9:65bfa5842a31ba4912029a03956f4e17', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

-- Changeset changelog/structure/14-insurance-expiring-soon-view.sql::add-insurance-expiring-soon-view::sander
CREATE VIEW insurance.insurance_expiring_soon AS
SELECT
    i.id,
    i.person_id,
    i.car_id,
    i.insurer_name,
    i.plan,
    i.amount,
    i.expiry_date,
    (i.expiry_date - CURRENT_DATE) AS days_left
FROM insurance.insurance i
WHERE (i.expiry_date - CURRENT_DATE) < 30;

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-insurance-expiring-soon-view', 'sander', 'changelog/structure/14-insurance-expiring-soon-view.sql', NOW(), 16, '9:368df0f6370ddcdbaf2d21ab6e463fb0', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

-- Changeset changelog/structure/15-search-indexes.sql::add-person-search-index::sander
CREATE INDEX person__combined_search__fts_idx ON person.person USING GIN ((
    to_tsvector('simple', coalesce(name, '')) ||
    to_tsvector('simple', coalesce(nickname, '')) ||
    to_tsvector('simple', coalesce(identity_code, ''))
));

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-person-search-index', 'sander', 'changelog/structure/15-search-indexes.sql', NOW(), 17, '9:a75974d44da9c2d40a21b2adb513dc6e', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

-- Changeset changelog/structure/15-search-indexes.sql::add-car-search-index::sander
CREATE INDEX car__combined_search__fts_idx ON car.car USING GIN ((
    to_tsvector('simple', coalesce(mark, '')) ||
    to_tsvector('simple', coalesce(model, ''))
));

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-car-search-index', 'sander', 'changelog/structure/15-search-indexes.sql', NOW(), 18, '9:cd6450efdd86def83032b2753e243dae', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

-- Changeset changelog/structure/15-search-indexes.sql::add-issuer-firm-search-index::sander
CREATE INDEX issuer_firm__combined_search__fts_idx ON issuer_firm.issuer_firm USING GIN (
    to_tsvector('simple', coalesce(firm_name, ''))
);

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-issuer-firm-search-index', 'sander', 'changelog/structure/15-search-indexes.sql', NOW(), 19, '9:0f665d23db7fb5220d0db2a4445e088c', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

-- Changeset changelog/structure/15-search-indexes.sql::add-insurance-search-index::sander
CREATE INDEX insurance__combined_search__fts_idx ON insurance.insurance USING GIN ((
    to_tsvector('simple', coalesce(insurer_name, '')) ||
    to_tsvector('simple', coalesce(plan, ''))
));

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-insurance-search-index', 'sander', 'changelog/structure/15-search-indexes.sql', NOW(), 20, '9:bfd6247ff456963ae1a62eedfef6140b', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '2654392273');

-- Release Database Lock
UPDATE public.databasechangeloglock SET LOCKED = FALSE, LOCKEDBY = NULL, LOCKGRANTED = NULL WHERE ID = 1;

