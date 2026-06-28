package com.example.controller;

import com.example.auth.security.RequiresRoleGroup;
import com.example.business.mapper.PersonMapper;
import com.example.business.useCase.CreatePerson;
import com.example.business.useCase.GetPerson;
import com.example.business.useCase.GetPersons;
import com.example.business.useCase.SearchPersons;
import com.example.business.useCase.UpdatePerson;
import com.example.openapi.api.PersonsApi;
import com.example.openapi.model.Person;
import com.example.openapi.model.PersonCreateRequest;
import com.example.openapi.model.PersonSearchRequest;
import com.example.openapi.model.PersonsResponse;
import com.example.openapi.model.UserInfoOutputModalRoleGroupsInner;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Controller
public class PersonController implements PersonsApi {

    private final GetPersons getPersons;
    private final GetPerson getPerson;
    private final CreatePerson createPerson;
    private final UpdatePerson updatePerson;
    private final SearchPersons searchPersons;

    public PersonController(
            GetPersons getPersons,
            GetPerson getPerson,
            CreatePerson createPerson,
            UpdatePerson updatePerson,
            SearchPersons searchPersons
    ) {
        this.getPersons = getPersons;
        this.getPerson = getPerson;
        this.createPerson = createPerson;
        this.updatePerson = updatePerson;
        this.searchPersons = searchPersons;
    }

    @Override
    public HttpResponse<com.example.openapi.model.@Valid Person> getPerson(Integer id) {
        var person = getPerson.execute(id.longValue());
        if (person == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(PersonMapper.mapToPerson(person));
    }

    @Override
    public HttpResponse<@NotNull List<@Valid Person>> getPersons() {
        List<Person> allPersons = PersonMapper.mapToPersons(getPersons.execute());
        return HttpResponse.ok(allPersons);
    }

    @Override
    @RequiresRoleGroup({UserInfoOutputModalRoleGroupsInner.END_USER, UserInfoOutputModalRoleGroupsInner.ADMIN})
    public HttpResponse<@Valid Person> createPerson(PersonCreateRequest personCreateRequest) {
        var created = createPerson.execute(personCreateRequest);
        return HttpResponse.ok(PersonMapper.mapToPerson(created));
    }

    @Override
    public HttpResponse<@Valid PersonsResponse> searchPersons(
            Integer page, Integer size, List<@NotNull String> sort, PersonSearchRequest personSearchRequest) {
        var result = searchPersons.execute(personSearchRequest, page, size, sort);
        PersonsResponse response = new PersonsResponse()
            .content(PersonMapper.mapToPersons(result.content()))
            .totalSize((int) result.totalSize())
            .totalPages(result.totalPages());
        return HttpResponse.ok(response);
    }

    @Override
    @RequiresRoleGroup({UserInfoOutputModalRoleGroupsInner.ADMIN})
    public HttpResponse<Void> updatePerson(Integer id, com.example.openapi.model.Person person) {
        boolean updated = updatePerson.execute(id.longValue(), person);
        return updated ? HttpResponse.ok() : HttpResponse.notFound();
    }
}
