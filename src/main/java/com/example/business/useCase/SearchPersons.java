package com.example.business.useCase;

import com.example.business.adapter.PersonAdapter;
import com.example.business.global.SortMapper;
import com.example.business.search.PersonSearchCriteriaMapper;
import com.example.jooq.person.tables.pojos.Person;
import com.example.openapi.model.PersonSearchRequest;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;

import java.util.List;
import java.util.Map;

import static com.example.jooq.person.tables.Person.PERSON_;

@Singleton
public class SearchPersons {

    private static final Map<String, Field<?>> SORTABLE_FIELDS = Map.of(
        "name", PERSON_.NAME,
        "nickname", PERSON_.NICKNAME,
        "identityCode", PERSON_.IDENTITY_CODE,
        "age", PERSON_.AGE
    );
    private static final List<String> DEFAULT_SORT = List.of("name:asc");

    private final PersonAdapter personAdapter;
    private final PersonSearchCriteriaMapper criteriaMapper;

    public SearchPersons(PersonAdapter personAdapter, PersonSearchCriteriaMapper criteriaMapper) {
        this.personAdapter = personAdapter;
        this.criteriaMapper = criteriaMapper;
    }

    @Transactional(readOnly = true)
    public Result execute(PersonSearchRequest request, Integer page, Integer size, List<String> sort) {
        PersonSearchRequest safeRequest = request == null ? new PersonSearchRequest() : request;
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? 50 : size;

        Condition condition = criteriaMapper.buildCondition(safeRequest);
        List<OrderField<?>> orderFields = SortMapper.mapSortFields(
            sort == null || sort.isEmpty() ? DEFAULT_SORT : sort, SORTABLE_FIELDS);

        List<Person> persons = personAdapter.search(condition, orderFields, safePage, safeSize);
        long totalSize = personAdapter.countSearch(condition);
        int totalPages = (int) Math.ceil(totalSize / (double) safeSize);

        return new Result(persons, totalSize, totalPages);
    }

    public record Result(List<Person> content, long totalSize, int totalPages) {
    }
}
