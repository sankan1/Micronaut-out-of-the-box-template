package com.example.business.search;

import com.example.openapi.model.InsuranceSearchRequest;
import com.example.util.search.TextSearchBuilder;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.impl.DSL;

import java.util.List;

import static com.example.jooq.car.tables.Car.CAR_;
import static com.example.jooq.insurance.tables.Insurance.INSURANCE_;
import static com.example.jooq.person.tables.Person.PERSON_;

@Singleton
public class InsuranceSearchCriteriaMapper {

    public Condition buildCondition(InsuranceSearchRequest request) {
        Condition condition = DSL.noCondition();

        if (StringUtils.isNotEmpty(request.getInsurerName())) {
            condition = condition.and(INSURANCE_.INSURER_NAME.containsIgnoreCase(request.getInsurerName()));
        }
        if (StringUtils.isNotEmpty(request.getPlan())) {
            condition = condition.and(INSURANCE_.PLAN.containsIgnoreCase(request.getPlan()));
        }
        if (request.getPersonId() != null) {
            condition = condition.and(INSURANCE_.PERSON_ID.eq(request.getPersonId().longValue()));
        }
        if (StringUtils.isNotEmpty(request.getPersonName())) {
            condition = condition.and(PERSON_.NAME.containsIgnoreCase(request.getPersonName()));
        }
        if (request.getCarId() != null) {
            condition = condition.and(INSURANCE_.CAR_ID.eq(request.getCarId().longValue()));
        }
        if (StringUtils.isNotEmpty(request.getCarMark())) {
            condition = condition.and(CAR_.MARK.containsIgnoreCase(request.getCarMark()));
        }
        if (StringUtils.isNotEmpty(request.getCarModel())) {
            condition = condition.and(CAR_.MODEL.containsIgnoreCase(request.getCarModel()));
        }
        if (StringUtils.isNotEmpty(request.getTextSearch())) {
            condition = condition.and(TextSearchBuilder.buildCondition(request.getTextSearch(),
                List.of(INSURANCE_.INSURER_NAME, INSURANCE_.PLAN)));
        }

        return condition;
    }
}
