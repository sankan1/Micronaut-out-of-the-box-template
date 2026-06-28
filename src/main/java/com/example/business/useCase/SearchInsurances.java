package com.example.business.useCase;

import com.example.business.adapter.InsuranceAdapter;
import com.example.business.global.SortMapper;
import com.example.business.search.InsuranceSearchCriteriaMapper;
import com.example.openapi.model.InsuranceSearchRequest;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;

import java.util.List;
import java.util.Map;

import static com.example.jooq.insurance.tables.Insurance.INSURANCE_;

@Singleton
public class SearchInsurances {

    private static final Map<String, Field<?>> SORTABLE_FIELDS = Map.of(
        "insurerName", INSURANCE_.INSURER_NAME,
        "plan", INSURANCE_.PLAN,
        "amount", INSURANCE_.AMOUNT,
        "expiryDate", INSURANCE_.EXPIRY_DATE
    );
    private static final List<String> DEFAULT_SORT = List.of("expiryDate:asc");

    private final InsuranceAdapter insuranceAdapter;
    private final InsuranceSearchCriteriaMapper criteriaMapper;

    public SearchInsurances(InsuranceAdapter insuranceAdapter, InsuranceSearchCriteriaMapper criteriaMapper) {
        this.insuranceAdapter = insuranceAdapter;
        this.criteriaMapper = criteriaMapper;
    }

    @Transactional(readOnly = true)
    public Result execute(InsuranceSearchRequest request, Integer page, Integer size, List<String> sort) {
        InsuranceSearchRequest safeRequest = request == null ? new InsuranceSearchRequest() : request;
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? 50 : size;

        Condition condition = criteriaMapper.buildCondition(safeRequest);
        List<OrderField<?>> orderFields = SortMapper.mapSortFields(
            sort == null || sort.isEmpty() ? DEFAULT_SORT : sort, SORTABLE_FIELDS);

        List<InsuranceAdapter.InsuranceRow> rows = insuranceAdapter.search(condition, orderFields, safePage, safeSize);
        long totalSize = insuranceAdapter.countSearch(condition);
        int totalPages = (int) Math.ceil(totalSize / (double) safeSize);

        return new Result(rows, totalSize, totalPages);
    }

    public record Result(List<InsuranceAdapter.InsuranceRow> content, long totalSize, int totalPages) {
    }
}
