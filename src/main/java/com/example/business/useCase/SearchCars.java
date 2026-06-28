package com.example.business.useCase;

import com.example.business.adapter.CarAdapter;
import com.example.business.global.SortMapper;
import com.example.business.search.CarSearchCriteriaMapper;
import com.example.openapi.model.CarSearchRequest;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;

import java.util.List;
import java.util.Map;

import static com.example.jooq.car.tables.Car.CAR_;

@Singleton
public class SearchCars {

    private static final Map<String, Field<?>> SORTABLE_FIELDS = Map.of(
        "mark", CAR_.MARK,
        "model", CAR_.MODEL
    );
    private static final List<String> DEFAULT_SORT = List.of("mark:asc");

    private final CarAdapter carAdapter;
    private final CarSearchCriteriaMapper criteriaMapper;

    public SearchCars(CarAdapter carAdapter, CarSearchCriteriaMapper criteriaMapper) {
        this.carAdapter = carAdapter;
        this.criteriaMapper = criteriaMapper;
    }

    @Transactional(readOnly = true)
    public Result execute(CarSearchRequest request, Integer page, Integer size, List<String> sort) {
        CarSearchRequest safeRequest = request == null ? new CarSearchRequest() : request;
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? 50 : size;

        Condition condition = criteriaMapper.buildCondition(safeRequest);
        List<OrderField<?>> orderFields = SortMapper.mapSortFields(
            sort == null || sort.isEmpty() ? DEFAULT_SORT : sort, SORTABLE_FIELDS);

        List<CarAdapter.CarRow> rows = carAdapter.search(condition, orderFields, safePage, safeSize);
        long totalSize = carAdapter.countSearch(condition);
        int totalPages = (int) Math.ceil(totalSize / (double) safeSize);

        return new Result(rows, totalSize, totalPages);
    }

    public record Result(List<CarAdapter.CarRow> content, long totalSize, int totalPages) {
    }
}
