package com.example.business.search;

import com.example.openapi.model.PersonSearchRequest;
import com.example.util.search.TextSearchBuilder;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Record1;
import org.jooq.Select;
import org.jooq.impl.DSL;

import java.util.List;

import static com.example.jooq.car.tables.Car.CAR_;
import static com.example.jooq.insurance.tables.Insurance.INSURANCE_;
import static com.example.jooq.issuer_firm.tables.IssuerFirm.ISSUER_FIRM_;
import static com.example.jooq.person.tables.Person.PERSON_;

@Singleton
public class PersonSearchCriteriaMapper {

    public Condition buildCondition(PersonSearchRequest request) {
        Condition condition = DSL.noCondition();

        if (StringUtils.isNotEmpty(request.getName())) {
            condition = condition.and(PERSON_.NAME.containsIgnoreCase(request.getName()));
        }
        if (StringUtils.isNotEmpty(request.getNickname())) {
            condition = condition.and(PERSON_.NICKNAME.containsIgnoreCase(request.getNickname()));
        }
        if (StringUtils.isNotEmpty(request.getIdentityCode())) {
            condition = condition.and(PERSON_.IDENTITY_CODE.containsIgnoreCase(request.getIdentityCode()));
        }
        if (StringUtils.isNotEmpty(request.getCarMark()) || StringUtils.isNotEmpty(request.getCarModel())) {
            condition = condition.and(PERSON_.ID.in(carOwnerIdsMatching(request)));
        }
        if (StringUtils.isNotEmpty(request.getIssuerFirmName())) {
            condition = condition.and(PERSON_.ID.in(issuerFirmOwnerIdsMatching(request.getIssuerFirmName())));
        }
        if (StringUtils.isNotEmpty(request.getInsurancePlan())) {
            condition = condition.and(PERSON_.ID.in(insuranceOwnerIdsMatching(request.getInsurancePlan())));
        }
        if (StringUtils.isNotEmpty(request.getTextSearch())) {
            condition = condition.and(TextSearchBuilder.buildCondition(request.getTextSearch(),
                List.of(PERSON_.NAME, PERSON_.NICKNAME, PERSON_.IDENTITY_CODE)));
        }

        return condition;
    }

    private Select<Record1<Long>> carOwnerIdsMatching(PersonSearchRequest request) {
        Condition carCondition = CAR_.OWNER_ID.isNotNull();
        if (StringUtils.isNotEmpty(request.getCarMark())) {
            carCondition = carCondition.and(CAR_.MARK.containsIgnoreCase(request.getCarMark()));
        }
        if (StringUtils.isNotEmpty(request.getCarModel())) {
            carCondition = carCondition.and(CAR_.MODEL.containsIgnoreCase(request.getCarModel()));
        }
        return DSL.select(CAR_.OWNER_ID).from(CAR_).where(carCondition);
    }

    private Select<Record1<Long>> issuerFirmOwnerIdsMatching(String firmName) {
        return DSL.select(CAR_.OWNER_ID)
            .from(CAR_)
            .join(ISSUER_FIRM_).on(ISSUER_FIRM_.CAR_ID.eq(CAR_.ID))
            .where(CAR_.OWNER_ID.isNotNull())
            .and(ISSUER_FIRM_.FIRM_NAME.containsIgnoreCase(firmName));
    }

    private Select<Record1<Long>> insuranceOwnerIdsMatching(String plan) {
        return DSL.select(INSURANCE_.PERSON_ID)
            .from(INSURANCE_)
            .where(INSURANCE_.PLAN.containsIgnoreCase(plan));
    }
}
