package com.example.business.search;

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
import static org.jooq.impl.DSL.select;

/**
 * Searches each table independently using its own GIN index, UNION ALLs the matching person ids,
 * then filters the main Person query — see full-text-search-guide.md.
 */
@Singleton
public class QuickPersonSearchCriteriaMapper {

    public Condition getQuickSearchCondition(String input) {
        if (StringUtils.isEmpty(input) || input.trim().isEmpty()) {
            return DSL.noCondition();
        }

        Select<Record1<Long>> unionedPersonIds = buildPersonFieldsQuery(input)
            .unionAll(buildCarFieldsQuery(input))
            .unionAll(buildIssuerFirmFieldsQuery(input))
            .unionAll(buildInsuranceFieldsQuery(input));

        Select<Record1<Long>> combinedPersonIds = DSL
            .selectDistinct(DSL.field(PERSON_.ID.getName(), Long.class))
            .from(unionedPersonIds.asTable("person_ids"));

        return PERSON_.ID.in(combinedPersonIds);
    }

    private Select<Record1<Long>> buildPersonFieldsQuery(String input) {
        Condition condition = TextSearchBuilder.buildCondition(input,
            List.of(PERSON_.NAME, PERSON_.NICKNAME, PERSON_.IDENTITY_CODE));
        return select(PERSON_.ID).from(PERSON_).where(condition);
    }

    private Select<Record1<Long>> buildCarFieldsQuery(String input) {
        Condition condition = TextSearchBuilder.buildCondition(input, List.of(CAR_.MARK, CAR_.MODEL));
        return select(CAR_.OWNER_ID).from(CAR_).where(CAR_.OWNER_ID.isNotNull().and(condition));
    }

    private Select<Record1<Long>> buildIssuerFirmFieldsQuery(String input) {
        Condition condition = TextSearchBuilder.buildCondition(input, List.of(ISSUER_FIRM_.FIRM_NAME));
        return select(CAR_.OWNER_ID)
            .from(ISSUER_FIRM_)
            .join(CAR_).on(CAR_.ID.eq(ISSUER_FIRM_.CAR_ID))
            .where(CAR_.OWNER_ID.isNotNull().and(condition));
    }

    private Select<Record1<Long>> buildInsuranceFieldsQuery(String input) {
        Condition condition = TextSearchBuilder.buildCondition(input,
            List.of(INSURANCE_.INSURER_NAME, INSURANCE_.PLAN));
        return select(INSURANCE_.PERSON_ID).from(INSURANCE_).where(condition);
    }
}
