package com.example.controller;

import com.example.business.mapper.PersonMapper;
import com.example.business.useCase.GetPersons;
import com.example.jooq.person.tables.pojos.Person;
import com.example.openapi.api.PersonApi;
import com.example.openapi.model.PersonModel;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Controller
public class PersonController implements PersonApi {

    private final GetPersons getPersons;

    private PersonController(
            GetPersons getPersons
    ) {
        this.getPersons = getPersons;
    }

    @Override
    public HttpResponse<@Valid PersonModel> getPersonById(Integer id) {
        return null;
    }

    @Override
    public HttpResponse<@NotNull List<@Valid PersonModel>> getPersons() {
        List<Person> persons = getPersons.execute();
        return HttpResponse.ok(PersonMapper.mapToPersonModels(persons));
    }
}
