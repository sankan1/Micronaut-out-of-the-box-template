package com.example.business.mapper;

import com.example.business.adapter.CarAdapter.CarRow;
import com.example.jooq.car.tables.pojos.Car;
import com.example.openapi.model.CarCreateRequest;

import java.util.List;

public final class CarMapper {

    public static List<com.example.openapi.model.Car> mapToCars(List<CarRow> rows) {
        return rows.stream().map(CarMapper::mapToCar).toList();
    }

    public static com.example.openapi.model.Car mapToCar(CarRow row) {
        return new com.example.openapi.model.Car(Math.toIntExact(row.id()), row.mark(), row.model())
            .ownerId(row.ownerId() == null ? null : Math.toIntExact(row.ownerId()))
            .ownerName(row.ownerName())
            .issuerFirmName(row.issuerFirmName());
    }

    public static Car mapToNewCar(CarCreateRequest request) {
        return new Car()
            .setMark(request.getMark())
            .setModel(request.getModel());
    }
}
