package com.example.business.adapter;

import com.example.jooq.car.tables.daos.CarDao;
import com.example.jooq.car.tables.pojos.Car;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.OrderField;
import org.jooq.Record6;
import org.jooq.SelectOnConditionStep;

import java.util.List;

import static com.example.jooq.car.tables.Car.CAR_;
import static com.example.jooq.issuer_firm.tables.IssuerFirm.ISSUER_FIRM_;
import static com.example.jooq.person.tables.Person.PERSON_;

@Singleton
public class CarAdapter {

    private final DSLContext dsl;
    private final CarDao dao;

    public CarAdapter(DSLContext dsl, CarDao dao) {
        this.dsl = dsl;
        this.dao = dao;
    }

    public Car getById(Long id) {
        return dao.fetchOneById(id);
    }

    public Car create(Car car) {
        dao.insert(car);
        return car;
    }

    public void update(Car car) {
        dao.update(car);
    }

    public void assignOwner(Long carId, Long ownerId) {
        dsl.update(CAR_).set(CAR_.OWNER_ID, ownerId).where(CAR_.ID.eq(carId)).execute();
    }

    public void delete(Long id) {
        dao.deleteById(id);
    }

    public CarRow getRowById(Long id) {
        return baseSelect().where(CAR_.ID.eq(id)).fetchOne(CarAdapter::toRow);
    }

    public List<CarRow> search(Condition condition, List<OrderField<?>> orderFields, int page, int size) {
        return baseSelect()
            .where(condition)
            .orderBy(orderFields)
            .limit(size)
            .offset(page * size)
            .fetch(CarAdapter::toRow);
    }

    public long countSearch(Condition condition) {
        return dsl.selectCount()
            .from(CAR_)
            .leftJoin(PERSON_).on(PERSON_.ID.eq(CAR_.OWNER_ID))
            .leftJoin(ISSUER_FIRM_).on(ISSUER_FIRM_.CAR_ID.eq(CAR_.ID))
            .where(condition)
            .fetchOne(0, Long.class);
    }

    private SelectOnConditionStep<Record6<Long, Long, String, String, String, String>> baseSelect() {
        return dsl.select(CAR_.ID, CAR_.OWNER_ID, PERSON_.NAME, CAR_.MARK, CAR_.MODEL, ISSUER_FIRM_.FIRM_NAME)
            .from(CAR_)
            .leftJoin(PERSON_).on(PERSON_.ID.eq(CAR_.OWNER_ID))
            .leftJoin(ISSUER_FIRM_).on(ISSUER_FIRM_.CAR_ID.eq(CAR_.ID));
    }

    private static CarRow toRow(Record6<Long, Long, String, String, String, String> record) {
        return new CarRow(
            record.value1(),
            record.value2(),
            record.value3(),
            record.value4(),
            record.value5(),
            record.value6()
        );
    }

    public record CarRow(Long id, Long ownerId, String ownerName, String mark, String model, String issuerFirmName) {
    }
}
