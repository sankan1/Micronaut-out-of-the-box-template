package com.example.business.useCase;

import com.example.business.adapter.InsuranceAdapter;
import com.example.business.mapper.InsuranceMapper;
import com.example.openapi.model.InsuranceUpdateRequest;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class UpdateInsurance {

    private final InsuranceAdapter insuranceAdapter;

    public UpdateInsurance(InsuranceAdapter insuranceAdapter) {
        this.insuranceAdapter = insuranceAdapter;
    }

    @Transactional
    public InsuranceAdapter.InsuranceRow execute(Long id, InsuranceUpdateRequest request) {
        var existing = insuranceAdapter.getById(id);
        if (existing == null) {
            return null;
        }
        InsuranceMapper.applyUpdate(existing, request);
        insuranceAdapter.update(existing);
        return insuranceAdapter.getRowById(id);
    }
}
