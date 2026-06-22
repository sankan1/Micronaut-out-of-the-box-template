package com.example.business.mapper;

import com.example.jooq.person.tables.pojos.Person;

import java.util.List;

public final class PersonMapper {

    public static List<com.example.openapi.model.Person> mapToPersons(List<Person> persons) {
        return persons.stream()
                .map(PersonMapper::mapToPerson)
                .toList();
    }

    private static com.example.openapi.model.Person mapToPerson(Person person) {
        return new com.example.openapi.model.Person(
                Math.toIntExact(person.getId()),
                person.getName()
        )
                .nickname(person.getNickname())
                .hobby(null);
    }
}
