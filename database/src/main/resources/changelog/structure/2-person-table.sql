--liquibase formatted sql

--changeset sander:add-person-table
CREATE TABLE person.person (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT person__id__pkey PRIMARY KEY (id)
);
--rollback DROP TABLE person.person;