package com.example.business.search;

import com.example.openapi.model.IssuerFirmSearchRequest;
import com.example.util.search.TextSearchBuilder;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.impl.DSL;

import java.util.List;

import static com.example.jooq.issuer_firm.tables.IssuerFirm.ISSUER_FIRM_;

@Singleton
public class IssuerFirmSearchCriteriaMapper {

    public Condition buildCondition(IssuerFirmSearchRequest request) {
        Condition condition = DSL.noCondition();

        if (StringUtils.isNotEmpty(request.getFirmName())) {
            condition = condition.and(ISSUER_FIRM_.FIRM_NAME.containsIgnoreCase(request.getFirmName()));
        }
        if (StringUtils.isNotEmpty(request.getTextSearch())) {
            condition = condition.and(TextSearchBuilder.buildCondition(request.getTextSearch(),
                List.of(ISSUER_FIRM_.FIRM_NAME)));
        }

        return condition;
    }
}
