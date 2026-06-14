package com.example.business.adapter;

import com.example.jooq.person.tables.daos.PersonDao;
import com.example.jooq.person.tables.pojos.Person;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;

import java.util.List;

@Singleton
public class PersonAdapter {

    private static final com.example.jooq.person.tables.Person PERSON = com.example.jooq.person.tables.Person.PERSON_;

    private final DSLContext dsl;
    private final PersonDao dao;

    public PersonAdapter(DSLContext dsl, PersonDao dao) {
        this.dsl = dsl;
        this.dao = dao;
    }

    public List<Person> getPersons() {
        return dsl.select(PERSON)
                .from(PERSON)
                .fetchInto(Person.class);
    }
}
