package com.example.business.useCase;

import com.example.business.adapter.CarAdapter;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class DeleteCar {

    private final CarAdapter carAdapter;

    public DeleteCar(CarAdapter carAdapter) {
        this.carAdapter = carAdapter;
    }

    @Transactional
    public void execute(Long id) {
        carAdapter.delete(id);
    }
}
