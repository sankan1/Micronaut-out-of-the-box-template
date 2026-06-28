package com.example.business.adapter;

import com.example.jooq.insurance.tables.daos.InsuranceDao;
import com.example.jooq.insurance.tables.pojos.Insurance;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.example.jooq.car.tables.Car.CAR_;
import static com.example.jooq.insurance.tables.Insurance.INSURANCE_;
import static com.example.jooq.insurance.tables.InsuranceExpiringSoon.INSURANCE_EXPIRING_SOON;
import static com.example.jooq.person.tables.Person.PERSON_;

@Singleton
public class InsuranceAdapter {

    private static final Field<Integer> DAYS_LEFT = DSL.field(
        "({0} - CURRENT_DATE)", Integer.class, INSURANCE_.EXPIRY_DATE);

    private final DSLContext dsl;
    private final InsuranceDao dao;

    public InsuranceAdapter(DSLContext dsl, InsuranceDao dao) {
        this.dsl = dsl;
        this.dao = dao;
    }

    public Insurance getById(Long id) {
        return dao.fetchOneById(id);
    }

    public Insurance create(Insurance insurance) {
        dao.insert(insurance);
        return insurance;
    }

    public void update(Insurance insurance) {
        dao.update(insurance);
    }

    public void delete(Long id) {
        dao.deleteById(id);
    }

    public InsuranceRow getRowById(Long id) {
        return baseSelect().where(INSURANCE_.ID.eq(id)).fetchOne(InsuranceAdapter::toRow);
    }

    public List<InsuranceRow> search(Condition condition, List<OrderField<?>> orderFields, int page, int size) {
        return baseSelect()
            .where(condition)
            .orderBy(orderFields)
            .limit(size)
            .offset(page * size)
            .fetch(InsuranceAdapter::toRow);
    }

    public long countSearch(Condition condition) {
        return dsl.selectCount()
            .from(INSURANCE_)
            .join(PERSON_).on(PERSON_.ID.eq(INSURANCE_.PERSON_ID))
            .join(CAR_).on(CAR_.ID.eq(INSURANCE_.CAR_ID))
            .where(condition)
            .fetchOne(0, Long.class);
    }

    public List<InsuranceRow> getExpiringSoon() {
        return dsl.select(List.of(INSURANCE_EXPIRING_SOON.ID, INSURANCE_EXPIRING_SOON.PERSON_ID, PERSON_.NAME,
                INSURANCE_EXPIRING_SOON.CAR_ID, CAR_.MARK, CAR_.MODEL,
                INSURANCE_EXPIRING_SOON.INSURER_NAME, INSURANCE_EXPIRING_SOON.PLAN,
                INSURANCE_EXPIRING_SOON.AMOUNT, INSURANCE_EXPIRING_SOON.EXPIRY_DATE,
                INSURANCE_EXPIRING_SOON.DAYS_LEFT))
            .from(INSURANCE_EXPIRING_SOON)
            .join(PERSON_).on(PERSON_.ID.eq(INSURANCE_EXPIRING_SOON.PERSON_ID))
            .join(CAR_).on(CAR_.ID.eq(INSURANCE_EXPIRING_SOON.CAR_ID))
            .orderBy(INSURANCE_EXPIRING_SOON.DAYS_LEFT.asc())
            .fetch(InsuranceAdapter::toRow);
    }

    private SelectJoinStep<Record> baseSelect() {
        return dsl.select(List.of(INSURANCE_.ID, INSURANCE_.PERSON_ID, PERSON_.NAME,
                INSURANCE_.CAR_ID, CAR_.MARK, CAR_.MODEL,
                INSURANCE_.INSURER_NAME, INSURANCE_.PLAN, INSURANCE_.AMOUNT, INSURANCE_.EXPIRY_DATE, DAYS_LEFT))
            .from(INSURANCE_)
            .join(PERSON_).on(PERSON_.ID.eq(INSURANCE_.PERSON_ID))
            .join(CAR_).on(CAR_.ID.eq(INSURANCE_.CAR_ID));
    }

    private static InsuranceRow toRow(Record record) {
        return new InsuranceRow(
            record.get(0, Long.class),
            record.get(1, Long.class),
            record.get(2, String.class),
            record.get(3, Long.class),
            record.get(4, String.class),
            record.get(5, String.class),
            record.get(6, String.class),
            record.get(7, String.class),
            record.get(8, BigDecimal.class),
            record.get(9, LocalDate.class),
            record.get(10, Integer.class)
        );
    }

    public record InsuranceRow(
        Long id, Long personId, String personName, Long carId, String carMark, String carModel,
        String insurerName, String plan, BigDecimal amount, LocalDate expiryDate, Integer daysLeft) {
    }
}
