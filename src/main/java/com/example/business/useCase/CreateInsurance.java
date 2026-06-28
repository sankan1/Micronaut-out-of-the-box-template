package com.example.business.useCase;

import com.example.business.adapter.InsuranceAdapter;
import com.example.business.mapper.InsuranceMapper;
import com.example.openapi.model.InsuranceCreateRequest;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class CreateInsurance {

    private final InsuranceAdapter insuranceAdapter;

    public CreateInsurance(InsuranceAdapter insuranceAdapter) {
        this.insuranceAdapter = insuranceAdapter;
    }

    @Transactional
    public InsuranceAdapter.InsuranceRow execute(InsuranceCreateRequest request) {
        var created = insuranceAdapter.create(InsuranceMapper.mapToNewInsurance(request));
        return insuranceAdapter.getRowById(created.getId());
    }
}
