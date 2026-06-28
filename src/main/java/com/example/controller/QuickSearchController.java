package com.example.controller;

import com.example.business.mapper.PersonMapper;
import com.example.business.useCase.QuickSearchPersons;
import com.example.openapi.api.QuickSearchApi;
import com.example.openapi.model.PersonsResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Controller
public class QuickSearchController implements QuickSearchApi {

    private final QuickSearchPersons quickSearchPersons;

    public QuickSearchController(QuickSearchPersons quickSearchPersons) {
        this.quickSearchPersons = quickSearchPersons;
    }

    @Override
    public HttpResponse<@Valid PersonsResponse> quickSearch(
            String searchText, Integer page, Integer size, List<@NotNull String> sort) {
        var result = quickSearchPersons.execute(searchText, page, size, sort);
        PersonsResponse response = new PersonsResponse()
            .content(PersonMapper.mapToPersons(result.content()))
            .totalSize((int) result.totalSize())
            .totalPages(result.totalPages());
        return HttpResponse.ok(response);
    }
}
