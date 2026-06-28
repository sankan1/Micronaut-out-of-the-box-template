package com.example.business.useCase;

import com.example.business.adapter.CarAdapter;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class GetCar {

    private final CarAdapter carAdapter;

    public GetCar(CarAdapter carAdapter) {
        this.carAdapter = carAdapter;
    }

    @Transactional(readOnly = true)
    public CarAdapter.CarRow execute(Long id) {
        return carAdapter.getRowById(id);
    }
}
