package com.example.business.useCase;

import com.example.business.adapter.InsuranceAdapter;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class DeleteInsurance {

    private final InsuranceAdapter insuranceAdapter;

    public DeleteInsurance(InsuranceAdapter insuranceAdapter) {
        this.insuranceAdapter = insuranceAdapter;
    }

    @Transactional
    public void execute(Long id) {
        insuranceAdapter.delete(id);
    }
}
