package com.example.business.useCase;

import com.example.business.adapter.CarAdapter;
import com.example.openapi.model.CarUpdateRequest;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class UpdateCar {

    private final CarAdapter carAdapter;

    public UpdateCar(CarAdapter carAdapter) {
        this.carAdapter = carAdapter;
    }

    @Transactional
    public CarAdapter.CarRow execute(Long id, CarUpdateRequest request) {
        var existing = carAdapter.getById(id);
        if (existing == null) {
            return null;
        }
        existing.setMark(request.getMark());
        existing.setModel(request.getModel());
        carAdapter.update(existing);
        return carAdapter.getRowById(id);
    }
}
