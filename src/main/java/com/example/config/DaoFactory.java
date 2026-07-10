package com.example.config;

import com.example.jooq.car.tables.daos.CarDao;
import com.example.jooq.document.tables.daos.DocumentDao;
import com.example.jooq.insurance.tables.daos.InsuranceDao;
import com.example.jooq.issuer_firm.tables.daos.IssuerFirmDao;
import com.example.jooq.person.tables.daos.PersonDao;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;

@Factory
public class DaoFactory {

    @Singleton
    public PersonDao personDao(DSLContext dslContext) {
        return new PersonDao(dslContext.configuration());
    }

    @Singleton
    public CarDao carDao(DSLContext dslContext) {
        return new CarDao(dslContext.configuration());
    }

    @Singleton
    public IssuerFirmDao issuerFirmDao(DSLContext dslContext) {
        return new IssuerFirmDao(dslContext.configuration());
    }

    @Singleton
    public InsuranceDao insuranceDao(DSLContext dslContext) {
        return new InsuranceDao(dslContext.configuration());
    }

    @Singleton
    public DocumentDao documentDao(DSLContext dslContext) {
        return new DocumentDao(dslContext.configuration());
    }
}
