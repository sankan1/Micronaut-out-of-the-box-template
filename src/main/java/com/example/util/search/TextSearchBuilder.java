package com.example.util.search;

import io.micronaut.core.util.StringUtils;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.List;

public final class TextSearchBuilder {

    private TextSearchBuilder() {
    }

    /**
     * Builds a full-text-search condition over the GIN-indexed `to_tsvector` concatenation of the given fields.
     * Plain text uses {@code plainto_tsquery}, "quoted phrases" use {@code phraseto_tsquery},
     * and input containing {@code *} falls back to a case-insensitive wildcard LIKE per field.
     */
    public static Condition buildCondition(String input, List<Field<String>> fields) {
        if (StringUtils.isEmpty(input) || fields.isEmpty()) {
            return DSL.noCondition();
        }

        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return DSL.noCondition();
        }

        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            String phrase = trimmed.substring(1, trimmed.length() - 1);
            return DSL.condition("{0} @@ phraseto_tsquery('simple', {1})", combinedTsVector(fields), DSL.val(phrase));
        }

        if (trimmed.contains("*")) {
            String likePattern = trimmed.replace('*', '%');
            return fields.stream()
                .<Condition>map(field -> field.likeIgnoreCase(likePattern))
                .reduce(Condition::or)
                .orElseGet(DSL::noCondition);
        }

        return DSL.condition("{0} @@ plainto_tsquery('simple', {1})", combinedTsVector(fields), DSL.val(trimmed));
    }

    private static Field<Object> combinedTsVector(List<Field<String>> fields) {
        return fields.stream()
            .<Field<Object>>map(field -> DSL.field("to_tsvector('simple', coalesce({0}, ''))", Object.class, field))
            .reduce((left, right) -> DSL.field("{0} || {1}", Object.class, left, right))
            .orElseThrow(() -> new IllegalArgumentException("At least one field is required"));
    }
}
