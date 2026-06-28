package com.example.controller;

import com.example.business.mapper.IssuerFirmMapper;
import com.example.business.useCase.SearchIssuerFirms;
import com.example.openapi.api.IssuerFirmsApi;
import com.example.openapi.model.IssuerFirmSearchRequest;
import com.example.openapi.model.IssuerFirmsResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Controller
public class IssuerFirmController implements IssuerFirmsApi {

    private final SearchIssuerFirms searchIssuerFirms;

    public IssuerFirmController(SearchIssuerFirms searchIssuerFirms) {
        this.searchIssuerFirms = searchIssuerFirms;
    }

    @Override
    public HttpResponse<@Valid IssuerFirmsResponse> searchIssuerFirms(
            Integer page, Integer size, List<@NotNull String> sort, IssuerFirmSearchRequest issuerFirmSearchRequest) {
        var result = searchIssuerFirms.execute(issuerFirmSearchRequest, page, size, sort);
        IssuerFirmsResponse response = new IssuerFirmsResponse()
            .content(IssuerFirmMapper.mapToIssuerFirms(result.content()))
            .totalSize((int) result.totalSize())
            .totalPages(result.totalPages());
        return HttpResponse.ok(response);
    }
}
