package com.example.business.useCase;

import com.example.business.adapter.IssuerFirmAdapter;
import com.example.business.global.SortMapper;
import com.example.business.search.IssuerFirmSearchCriteriaMapper;
import com.example.openapi.model.IssuerFirmSearchRequest;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;

import java.util.List;
import java.util.Map;

import static com.example.jooq.issuer_firm.tables.IssuerFirm.ISSUER_FIRM_;

@Singleton
public class SearchIssuerFirms {

    private static final Map<String, Field<?>> SORTABLE_FIELDS = Map.of(
        "firmName", ISSUER_FIRM_.FIRM_NAME
    );
    private static final List<String> DEFAULT_SORT = List.of("firmName:asc");

    private final IssuerFirmAdapter issuerFirmAdapter;
    private final IssuerFirmSearchCriteriaMapper criteriaMapper;

    public SearchIssuerFirms(IssuerFirmAdapter issuerFirmAdapter, IssuerFirmSearchCriteriaMapper criteriaMapper) {
        this.issuerFirmAdapter = issuerFirmAdapter;
        this.criteriaMapper = criteriaMapper;
    }

    @Transactional(readOnly = true)
    public Result execute(IssuerFirmSearchRequest request, Integer page, Integer size, List<String> sort) {
        IssuerFirmSearchRequest safeRequest = request == null ? new IssuerFirmSearchRequest() : request;
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? 50 : size;

        Condition condition = criteriaMapper.buildCondition(safeRequest);
        List<OrderField<?>> orderFields = SortMapper.mapSortFields(
            sort == null || sort.isEmpty() ? DEFAULT_SORT : sort, SORTABLE_FIELDS);

        List<IssuerFirmAdapter.IssuerFirmRow> rows = issuerFirmAdapter.search(condition, orderFields, safePage, safeSize);
        long totalSize = issuerFirmAdapter.countSearch(condition);
        int totalPages = (int) Math.ceil(totalSize / (double) safeSize);

        return new Result(rows, totalSize, totalPages);
    }

    public record Result(List<IssuerFirmAdapter.IssuerFirmRow> content, long totalSize, int totalPages) {
    }
}
