package com.example.business.useCase;

import com.example.business.adapter.CarAdapter;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class AssignCarOwner {

    private final CarAdapter carAdapter;

    public AssignCarOwner(CarAdapter carAdapter) {
        this.carAdapter = carAdapter;
    }

    @Transactional
    public CarAdapter.CarRow execute(Long carId, Long personId) {
        if (carAdapter.getById(carId) == null) {
            return null;
        }
        carAdapter.assignOwner(carId, personId);
        return carAdapter.getRowById(carId);
    }
}
