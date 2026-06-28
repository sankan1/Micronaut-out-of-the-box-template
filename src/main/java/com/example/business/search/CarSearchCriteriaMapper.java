package com.example.business.search;

import com.example.openapi.model.CarSearchRequest;
import com.example.util.search.TextSearchBuilder;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.impl.DSL;

import java.util.List;

import static com.example.jooq.car.tables.Car.CAR_;
import static com.example.jooq.issuer_firm.tables.IssuerFirm.ISSUER_FIRM_;
import static com.example.jooq.person.tables.Person.PERSON_;

@Singleton
public class CarSearchCriteriaMapper {

    public Condition buildCondition(CarSearchRequest request) {
        Condition condition = DSL.noCondition();

        if (StringUtils.isNotEmpty(request.getMark())) {
            condition = condition.and(CAR_.MARK.containsIgnoreCase(request.getMark()));
        }
        if (StringUtils.isNotEmpty(request.getModel())) {
            condition = condition.and(CAR_.MODEL.containsIgnoreCase(request.getModel()));
        }
        if (request.getOwnerId() != null) {
            condition = condition.and(CAR_.OWNER_ID.eq(request.getOwnerId().longValue()));
        }
        if (StringUtils.isNotEmpty(request.getOwnerName())) {
            condition = condition.and(PERSON_.NAME.containsIgnoreCase(request.getOwnerName()));
        }
        if (StringUtils.isNotEmpty(request.getIssuerFirmName())) {
            condition = condition.and(ISSUER_FIRM_.FIRM_NAME.containsIgnoreCase(request.getIssuerFirmName()));
        }
        if (Boolean.TRUE.equals(request.getUnownedOnly())) {
            condition = condition.and(CAR_.OWNER_ID.isNull());
        }
        if (StringUtils.isNotEmpty(request.getTextSearch())) {
            condition = condition.and(TextSearchBuilder.buildCondition(request.getTextSearch(),
                List.of(CAR_.MARK, CAR_.MODEL)));
        }

        return condition;
    }
}
