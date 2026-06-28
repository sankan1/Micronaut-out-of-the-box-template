package com.example.business.global;

import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.SortOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SortMapper {

    private SortMapper() {
    }

    /**
     * Parses "field:asc" / "field:desc" strings (the shape the frontend already sends) into jOOQ
     * OrderFields, restricted to the given allow-list of sortable fields per entity.
     */
    public static List<OrderField<?>> mapSortFields(List<String> sort, Map<String, Field<?>> sortableFields) {
        List<OrderField<?>> orderFields = new ArrayList<>();
        if (sort == null) {
            return orderFields;
        }
        for (String entry : sort) {
            OrderField<?> orderField = toOrderField(entry, sortableFields);
            if (orderField != null) {
                orderFields.add(orderField);
            }
        }
        return orderFields;
    }

    private static OrderField<?> toOrderField(String entry, Map<String, Field<?>> sortableFields) {
        String[] parts = entry.split(":", 2);
        Field<?> field = sortableFields.get(parts[0]);
        if (field == null) {
            return null;
        }
        boolean descending = parts.length > 1 && "desc".equalsIgnoreCase(parts[1]);
        return sortField(field, descending);
    }

    private static <T> OrderField<?> sortField(Field<T> field, boolean descending) {
        return descending ? field.sort(SortOrder.DESC) : field.sort(SortOrder.ASC);
    }
}
