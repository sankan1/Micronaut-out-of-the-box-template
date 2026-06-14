package com.example.business.mapper;

import com.example.jooq.person.tables.pojos.Person;
import com.example.openapi.model.PersonModel;

import java.util.List;

public final class PersonMapper {

    public static List<PersonModel> mapToPersonModels(List<Person> persons) {
        return persons.stream()
                .map(PersonMapper::mapToPersonModel)
                .toList();
    }

    private static PersonModel mapToPersonModel(Person person) {
        return new PersonModel()
                .id(person.getId())
                .name(person.getName())
                .nickname(person.getNickname());
    }
}
