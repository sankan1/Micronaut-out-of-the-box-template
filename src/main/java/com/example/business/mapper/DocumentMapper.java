package com.example.business.mapper;

import com.example.business.adapter.DocumentAdapter.ContextRow;
import com.example.jooq.document.tables.pojos.Document;

import java.util.List;

public final class DocumentMapper {

    public static List<com.example.openapi.model.Document> mapToDocuments(List<Document> documents) {
        return documents.stream().map(DocumentMapper::mapToDocument).toList();
    }

    public static com.example.openapi.model.Document mapToDocument(Document document) {
        return new com.example.openapi.model.Document(
                Math.toIntExact(document.getId()),
                document.getTitle(),
                document.getContentText())
            .createdAt(document.getCreatedAt() == null ? null : document.getCreatedAt().toZonedDateTime());
    }

    public static List<com.example.openapi.model.Document> mapContextRows(List<ContextRow> rows) {
        return rows.stream().map(DocumentMapper::mapContextRow).toList();
    }

    public static com.example.openapi.model.Document mapContextRow(ContextRow row) {
        return new com.example.openapi.model.Document(
                Math.toIntExact(row.id()),
                row.title(),
                row.contentText())
            .createdAt(row.createdAt() == null ? null : row.createdAt().toZonedDateTime())
            .similarity(row.similarity());
    }
}
