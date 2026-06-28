--liquibase formatted sql

--changeset sander:add-car-table
CREATE TABLE car.car (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    owner_id BIGINT,
    mark VARCHAR(255) NOT NULL,
    model VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT car__id__pkey PRIMARY KEY (id),
    CONSTRAINT car__owner_id__fkey FOREIGN KEY (owner_id) REFERENCES person.person (id)
);
--rollback DROP TABLE car.car;
