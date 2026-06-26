package com.example.controller;

import com.example.business.mapper.PersonMapper;
import com.example.business.useCase.GetPersons;
import com.example.openapi.api.PersonsApi;
import com.example.openapi.model.Person;
import com.example.openapi.model.PersonSearchRequest;
import com.example.openapi.model.PersonsResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Controller
public class PersonController implements PersonsApi {

    private final GetPersons getPersons;

    public PersonController(
            GetPersons getPersons
    ) {
        this.getPersons = getPersons;
    }

    @Override
    public HttpResponse<com.example.openapi.model.@Valid Person> getPerson(Integer id) {
        return null;
    }

    @Override
    public HttpResponse<@NotNull List<@Valid Person>> getPersons() {
        List<Person> allPersons = PersonMapper.mapToPersons(getPersons.execute());
        return HttpResponse.ok(allPersons);
    }

    @Override
    public HttpResponse<@Valid PersonsResponse> searchPersons(Integer page, Integer size, List<@NotNull String> sort, PersonSearchRequest personSearchRequest) {
        return null;
    }

    @Override
    public HttpResponse<Void> updatePerson(Integer id, com.example.openapi.model.Person person) {
        return null;
    }
}
