package com.example.config;

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
}
