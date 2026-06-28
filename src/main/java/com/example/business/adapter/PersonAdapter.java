package com.example.business.adapter;

import com.example.jooq.person.tables.daos.PersonDao;
import com.example.jooq.person.tables.pojos.Person;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.OrderField;

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

    public Person getById(Long id) {
        return dao.fetchOneById(id);
    }

    public Person create(Person person) {
        dao.insert(person);
        return person;
    }

    public void update(Person person) {
        dao.update(person);
    }

    public List<Person> search(Condition condition, List<OrderField<?>> orderFields, int page, int size) {
        return dsl.selectFrom(PERSON)
                .where(condition)
                .orderBy(orderFields)
                .limit(size)
                .offset(page * size)
                .fetchInto(Person.class);
    }

    public long countSearch(Condition condition) {
        return dsl.selectCount()
                .from(PERSON)
                .where(condition)
                .fetchOne(0, Long.class);
    }
}
