package com.example.business.useCase;

import com.example.business.adapter.PersonAdapter;
import com.example.jooq.person.tables.pojos.Person;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class GetPerson {

    private final PersonAdapter personAdapter;

    public GetPerson(PersonAdapter personAdapter) {
        this.personAdapter = personAdapter;
    }

    @Transactional(readOnly = true)
    public Person execute(Long id) {
        return personAdapter.getById(id);
    }
}
