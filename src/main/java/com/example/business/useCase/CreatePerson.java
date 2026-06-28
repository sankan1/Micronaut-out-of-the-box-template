package com.example.business.useCase;

import com.example.business.adapter.PersonAdapter;
import com.example.business.mapper.PersonMapper;
import com.example.jooq.person.tables.pojos.Person;
import com.example.openapi.model.PersonCreateRequest;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class CreatePerson {

    private final PersonAdapter personAdapter;

    public CreatePerson(PersonAdapter personAdapter) {
        this.personAdapter = personAdapter;
    }

    @Transactional
    public Person execute(PersonCreateRequest request) {
        return personAdapter.create(PersonMapper.mapToNewPerson(request));
    }
}
