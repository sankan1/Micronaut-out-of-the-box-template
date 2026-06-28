package com.example.business.mapper;

import com.example.jooq.person.tables.pojos.Person;
import com.example.openapi.model.PersonCreateRequest;

import java.util.List;

public final class PersonMapper {

    public static List<com.example.openapi.model.Person> mapToPersons(List<Person> persons) {
        return persons.stream()
                .map(PersonMapper::mapToPerson)
                .toList();
    }

    public static com.example.openapi.model.Person mapToPerson(Person person) {
        return new com.example.openapi.model.Person(
                Math.toIntExact(person.getId()),
                person.getName()
        )
                .nickname(person.getNickname())
                .identityCode(person.getIdentityCode())
                .age(person.getAge())
                .hobby(null);
    }

    public static Person mapToNewPerson(PersonCreateRequest request) {
        return new Person()
                .setName(request.getName())
                .setNickname(request.getNickname())
                .setIdentityCode(request.getIdentityCode())
                .setAge(request.getAge());
    }

    public static void applyUpdate(Person existing, com.example.openapi.model.Person update) {
        existing.setName(update.getName());
        existing.setNickname(update.getNickname());
        existing.setIdentityCode(update.getIdentityCode());
        existing.setAge(update.getAge());
    }
}
