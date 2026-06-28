package com.example.business.useCase;

import com.example.business.adapter.PersonAdapter;
import com.example.business.mapper.PersonMapper;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class UpdatePerson {

    private final PersonAdapter personAdapter;

    public UpdatePerson(PersonAdapter personAdapter) {
        this.personAdapter = personAdapter;
    }

    @Transactional
    public boolean execute(Long id, com.example.openapi.model.Person update) {
        var existing = personAdapter.getById(id);
        if (existing == null) {
            return false;
        }
        PersonMapper.applyUpdate(existing, update);
        personAdapter.update(existing);
        return true;
    }
}
