package com.example.business.adapter;

import com.example.jooq.issuer_firm.tables.daos.IssuerFirmDao;
import com.example.jooq.issuer_firm.tables.pojos.IssuerFirm;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.OrderField;
import org.jooq.Record5;
import org.jooq.SelectOnConditionStep;

import java.util.List;

import static com.example.jooq.car.tables.Car.CAR_;
import static com.example.jooq.issuer_firm.tables.IssuerFirm.ISSUER_FIRM_;

@Singleton
public class IssuerFirmAdapter {

    private final DSLContext dsl;
    private final IssuerFirmDao dao;

    public IssuerFirmAdapter(DSLContext dsl, IssuerFirmDao dao) {
        this.dsl = dsl;
        this.dao = dao;
    }

    public IssuerFirm create(IssuerFirm issuerFirm) {
        dao.insert(issuerFirm);
        return issuerFirm;
    }

    public List<IssuerFirmRow> search(Condition condition, List<OrderField<?>> orderFields, int page, int size) {
        return baseSelect()
            .where(condition)
            .orderBy(orderFields)
            .limit(size)
            .offset(page * size)
            .fetch(IssuerFirmAdapter::toRow);
    }

    public long countSearch(Condition condition) {
        return dsl.selectCount()
            .from(ISSUER_FIRM_)
            .join(CAR_).on(CAR_.ID.eq(ISSUER_FIRM_.CAR_ID))
            .where(condition)
            .fetchOne(0, Long.class);
    }

    private SelectOnConditionStep<Record5<Long, Long, String, String, String>> baseSelect() {
        return dsl.select(ISSUER_FIRM_.ID, ISSUER_FIRM_.CAR_ID, ISSUER_FIRM_.FIRM_NAME, CAR_.MARK, CAR_.MODEL)
            .from(ISSUER_FIRM_)
            .join(CAR_).on(CAR_.ID.eq(ISSUER_FIRM_.CAR_ID));
    }

    private static IssuerFirmRow toRow(Record5<Long, Long, String, String, String> record) {
        return new IssuerFirmRow(record.value1(), record.value2(), record.value3(), record.value4(), record.value5());
    }

    public record IssuerFirmRow(Long id, Long carId, String firmName, String carMark, String carModel) {
    }
}
