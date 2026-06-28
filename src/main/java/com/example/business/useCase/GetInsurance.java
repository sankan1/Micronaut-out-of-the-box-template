package com.example.business.useCase;

import com.example.business.adapter.InsuranceAdapter;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class GetInsurance {

    private final InsuranceAdapter insuranceAdapter;

    public GetInsurance(InsuranceAdapter insuranceAdapter) {
        this.insuranceAdapter = insuranceAdapter;
    }

    @Transactional(readOnly = true)
    public InsuranceAdapter.InsuranceRow execute(Long id) {
        return insuranceAdapter.getRowById(id);
    }
}
