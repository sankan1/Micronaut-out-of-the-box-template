--liquibase formatted sql

--changeset sander:add-insurance-table
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
--rollback DROP TABLE insurance.insurance;
