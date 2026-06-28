package com.example.controller;

import com.example.auth.security.RequiresRoleGroup;
import com.example.business.mapper.InsuranceMapper;
import com.example.business.useCase.CreateInsurance;
import com.example.business.useCase.DeleteInsurance;
import com.example.business.useCase.GetExpiringSoonInsurances;
import com.example.business.useCase.GetInsurance;
import com.example.business.useCase.SearchInsurances;
import com.example.business.useCase.UpdateInsurance;
import com.example.openapi.api.InsurancesApi;
import com.example.openapi.model.Insurance;
import com.example.openapi.model.InsuranceCreateRequest;
import com.example.openapi.model.InsuranceSearchRequest;
import com.example.openapi.model.InsuranceUpdateRequest;
import com.example.openapi.model.InsurancesResponse;
import com.example.openapi.model.UserInfoOutputModalRoleGroupsInner;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Controller
public class InsuranceController implements InsurancesApi {

    private final SearchInsurances searchInsurances;
    private final GetInsurance getInsurance;
    private final CreateInsurance createInsurance;
    private final UpdateInsurance updateInsurance;
    private final DeleteInsurance deleteInsurance;
    private final GetExpiringSoonInsurances getExpiringSoonInsurances;
    private final InsuranceWebSocket insuranceWebSocket;

    public InsuranceController(
            SearchInsurances searchInsurances,
            GetInsurance getInsurance,
            CreateInsurance createInsurance,
            UpdateInsurance updateInsurance,
            DeleteInsurance deleteInsurance,
            GetExpiringSoonInsurances getExpiringSoonInsurances,
            InsuranceWebSocket insuranceWebSocket
    ) {
        this.searchInsurances = searchInsurances;
        this.getInsurance = getInsurance;
        this.createInsurance = createInsurance;
        this.updateInsurance = updateInsurance;
        this.deleteInsurance = deleteInsurance;
        this.getExpiringSoonInsurances = getExpiringSoonInsurances;
        this.insuranceWebSocket = insuranceWebSocket;
    }

    @Override
    public HttpResponse<@Valid InsurancesResponse> searchInsurances(
            Integer page, Integer size, List<@NotNull String> sort, InsuranceSearchRequest insuranceSearchRequest) {
        var result = searchInsurances.execute(insuranceSearchRequest, page, size, sort);
        InsurancesResponse response = new InsurancesResponse()
            .content(InsuranceMapper.mapToInsurances(result.content()))
            .totalSize((int) result.totalSize())
            .totalPages(result.totalPages());
        return HttpResponse.ok(response);
    }

    @Override
    public HttpResponse<@Valid Insurance> getInsurance(Integer id) {
        var row = getInsurance.execute(id.longValue());
        if (row == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(InsuranceMapper.mapToInsurance(row));
    }

    @Override
    @RequiresRoleGroup({UserInfoOutputModalRoleGroupsInner.HEAD_USER, UserInfoOutputModalRoleGroupsInner.ADMIN})
    public HttpResponse<@Valid Insurance> createInsurance(InsuranceCreateRequest insuranceCreateRequest) {
        var row = createInsurance.execute(insuranceCreateRequest);
        insuranceWebSocket.broadcastUpdate();
        return HttpResponse.ok(InsuranceMapper.mapToInsurance(row));
    }

    @Override
    @RequiresRoleGroup({UserInfoOutputModalRoleGroupsInner.ADMIN})
    public HttpResponse<@Valid Insurance> updateInsurance(Integer id, InsuranceUpdateRequest insuranceUpdateRequest) {
        var row = updateInsurance.execute(id.longValue(), insuranceUpdateRequest);
        if (row == null) {
            return HttpResponse.notFound();
        }
        insuranceWebSocket.broadcastUpdate();
        return HttpResponse.ok(InsuranceMapper.mapToInsurance(row));
    }

    @Override
    @RequiresRoleGroup({UserInfoOutputModalRoleGroupsInner.ADMIN})
    public HttpResponse<Void> deleteInsurance(Integer id) {
        deleteInsurance.execute(id.longValue());
        insuranceWebSocket.broadcastUpdate();
        return HttpResponse.noContent();
    }

    @Override
    public HttpResponse<@NotNull List<@Valid Insurance>> getExpiringSoonInsurances() {
        return HttpResponse.ok(InsuranceMapper.mapToInsurances(getExpiringSoonInsurances.execute()));
    }
}
