-- Create Database Lock Table
CREATE TABLE public.databasechangeloglock (ID INTEGER NOT NULL, LOCKED BOOLEAN NOT NULL, LOCKGRANTED TIMESTAMP WITHOUT TIME ZONE, LOCKEDBY VARCHAR(255), CONSTRAINT databasechangeloglock_pkey PRIMARY KEY (ID));

-- Initialize Database Lock Table
DELETE FROM public.databasechangeloglock;

INSERT INTO public.databasechangeloglock (ID, LOCKED) VALUES (1, FALSE);

-- Lock Database
UPDATE public.databasechangeloglock SET LOCKED = TRUE, LOCKEDBY = 'sander (192.168.1.231)', LOCKGRANTED = NOW() WHERE ID = 1 AND LOCKED = FALSE;

-- Create Database Change Log Table
CREATE TABLE public.databasechangelog (ID VARCHAR(255) NOT NULL, AUTHOR VARCHAR(255) NOT NULL, FILENAME VARCHAR(255) NOT NULL, DATEEXECUTED TIMESTAMP WITHOUT TIME ZONE NOT NULL, ORDEREXECUTED INTEGER NOT NULL, EXECTYPE VARCHAR(10) NOT NULL, MD5SUM VARCHAR(35), DESCRIPTION VARCHAR(255), COMMENTS VARCHAR(255), TAG VARCHAR(255), LIQUIBASE VARCHAR(20), CONTEXTS VARCHAR(255), LABELS VARCHAR(255), DEPLOYMENT_ID VARCHAR(10));

-- *********************************************************************
-- Update Database Script
-- *********************************************************************
-- Change Log: changelog/db.changelog-master.yaml
-- Ran at: 6/14/26, 5:28 PM
-- Against: test@jdbc:postgresql://localhost:58561/test?loggerLevel=OFF
-- Liquibase version: 4.29.2
-- *********************************************************************

-- Changeset changelog/structure/1-person-schema.sql::add-person-schema::sander
CREATE SCHEMA person;

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-person-schema', 'sander', 'changelog/structure/1-person-schema.sql', NOW(), 1, '9:4d4937eff7a4f5ff61362e8bd729c555', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '1447312276');

-- Changeset changelog/structure/2-person-table.sql::add-person-table::sander
CREATE TABLE person.person (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT person__id__pkey PRIMARY KEY (id)
);

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-person-table', 'sander', 'changelog/structure/2-person-table.sql', NOW(), 2, '9:f555528fa823edf1d4330f605159d538', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '1447312276');

-- Changeset changelog/structure/3-person-table-addition.sql::add-person-table-column::sander
ALTER TABLE person.person ADD COLUMN nickname VARCHAR(255);

INSERT INTO public.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, MD5SUM, DESCRIPTION, COMMENTS, EXECTYPE, CONTEXTS, LABELS, LIQUIBASE, DEPLOYMENT_ID) VALUES ('add-person-table-column', 'sander', 'changelog/structure/3-person-table-addition.sql', NOW(), 3, '9:a0135841a3add18f3a1465397c50ad22', 'sql', '', 'EXECUTED', NULL, NULL, '4.29.2', '1447312276');

-- Release Database Lock
UPDATE public.databasechangeloglock SET LOCKED = FALSE, LOCKEDBY = NULL, LOCKGRANTED = NULL WHERE ID = 1;

