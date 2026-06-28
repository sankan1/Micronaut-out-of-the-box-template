package com.example.controller;

import com.example.auth.security.RequiresRoleGroup;
import com.example.business.mapper.CarMapper;
import com.example.business.useCase.AssignCarOwner;
import com.example.business.useCase.CreateCar;
import com.example.business.useCase.DeleteCar;
import com.example.business.useCase.GetCar;
import com.example.business.useCase.SearchCars;
import com.example.business.useCase.UpdateCar;
import com.example.openapi.api.CarsApi;
import com.example.openapi.model.Car;
import com.example.openapi.model.CarCreateRequest;
import com.example.openapi.model.CarOwnerAssignRequest;
import com.example.openapi.model.CarSearchRequest;
import com.example.openapi.model.CarUpdateRequest;
import com.example.openapi.model.CarsResponse;
import com.example.openapi.model.UserInfoOutputModalRoleGroupsInner;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Controller
public class CarController implements CarsApi {

    private final SearchCars searchCars;
    private final GetCar getCar;
    private final CreateCar createCar;
    private final UpdateCar updateCar;
    private final AssignCarOwner assignCarOwner;
    private final DeleteCar deleteCar;

    public CarController(
            SearchCars searchCars,
            GetCar getCar,
            CreateCar createCar,
            UpdateCar updateCar,
            AssignCarOwner assignCarOwner,
            DeleteCar deleteCar
    ) {
        this.searchCars = searchCars;
        this.getCar = getCar;
        this.createCar = createCar;
        this.updateCar = updateCar;
        this.assignCarOwner = assignCarOwner;
        this.deleteCar = deleteCar;
    }

    @Override
    public HttpResponse<@Valid CarsResponse> searchCars(
            Integer page, Integer size, List<@NotNull String> sort, CarSearchRequest carSearchRequest) {
        var result = searchCars.execute(carSearchRequest, page, size, sort);
        CarsResponse response = new CarsResponse()
            .content(CarMapper.mapToCars(result.content()))
            .totalSize((int) result.totalSize())
            .totalPages(result.totalPages());
        return HttpResponse.ok(response);
    }

    @Override
    public HttpResponse<@Valid Car> getCar(Integer id) {
        var row = getCar.execute(id.longValue());
        if (row == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(CarMapper.mapToCar(row));
    }

    @Override
    @RequiresRoleGroup({UserInfoOutputModalRoleGroupsInner.END_USER, UserInfoOutputModalRoleGroupsInner.ADMIN})
    public HttpResponse<@Valid Car> createCar(CarCreateRequest carCreateRequest) {
        var row = createCar.execute(carCreateRequest);
        return HttpResponse.ok(CarMapper.mapToCar(row));
    }

    @Override
    @RequiresRoleGroup({UserInfoOutputModalRoleGroupsInner.ADMIN})
    public HttpResponse<@Valid Car> updateCar(Integer id, CarUpdateRequest carUpdateRequest) {
        var row = updateCar.execute(id.longValue(), carUpdateRequest);
        if (row == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(CarMapper.mapToCar(row));
    }

    @Override
    @RequiresRoleGroup({UserInfoOutputModalRoleGroupsInner.HEAD_USER, UserInfoOutputModalRoleGroupsInner.ADMIN})
    public HttpResponse<@Valid Car> assignCarOwner(Integer id, CarOwnerAssignRequest carOwnerAssignRequest) {
        var row = assignCarOwner.execute(id.longValue(), carOwnerAssignRequest.getPersonId().longValue());
        if (row == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(CarMapper.mapToCar(row));
    }

    @Override
    @RequiresRoleGroup({UserInfoOutputModalRoleGroupsInner.ADMIN})
    public HttpResponse<Void> deleteCar(Integer id) {
        deleteCar.execute(id.longValue());
        return HttpResponse.noContent();
    }
}
