package com.example.business.useCase;

import com.example.business.adapter.CarAdapter;
import com.example.business.adapter.IssuerFirmAdapter;
import com.example.business.mapper.CarMapper;
import com.example.jooq.car.tables.pojos.Car;
import com.example.jooq.issuer_firm.tables.pojos.IssuerFirm;
import com.example.openapi.model.CarCreateRequest;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class CreateCar {

    private final CarAdapter carAdapter;
    private final IssuerFirmAdapter issuerFirmAdapter;

    public CreateCar(CarAdapter carAdapter, IssuerFirmAdapter issuerFirmAdapter) {
        this.carAdapter = carAdapter;
        this.issuerFirmAdapter = issuerFirmAdapter;
    }

    @Transactional
    public CarAdapter.CarRow execute(CarCreateRequest request) {
        Car car = carAdapter.create(CarMapper.mapToNewCar(request));
        issuerFirmAdapter.create(new IssuerFirm().setCarId(car.getId()).setFirmName(request.getIssuerFirmName()));
        return carAdapter.getRowById(car.getId());
    }
}
