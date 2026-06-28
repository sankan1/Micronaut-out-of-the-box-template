package com.example.business.useCase;

import com.example.business.adapter.InsuranceAdapter;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class GetExpiringSoonInsurances {

    private final InsuranceAdapter insuranceAdapter;

    public GetExpiringSoonInsurances(InsuranceAdapter insuranceAdapter) {
        this.insuranceAdapter = insuranceAdapter;
    }

    @Transactional(readOnly = true)
    public List<InsuranceAdapter.InsuranceRow> execute() {
        return insuranceAdapter.getExpiringSoon();
    }
}
