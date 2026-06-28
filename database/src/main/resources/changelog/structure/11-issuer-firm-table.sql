--liquibase formatted sql

--changeset sander:add-issuer-firm-table
CREATE TABLE issuer_firm.issuer_firm (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    car_id BIGINT NOT NULL,
    firm_name VARCHAR(255) NOT NULL,
    CONSTRAINT issuer_firm__id__pkey PRIMARY KEY (id),
    CONSTRAINT issuer_firm__car_id__fkey FOREIGN KEY (car_id) REFERENCES car.car (id)
);
--rollback DROP TABLE issuer_firm.issuer_firm;
