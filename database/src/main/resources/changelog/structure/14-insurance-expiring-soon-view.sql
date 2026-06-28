--liquibase formatted sql

--changeset sander:add-insurance-expiring-soon-view
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
--rollback DROP VIEW insurance.insurance_expiring_soon;
