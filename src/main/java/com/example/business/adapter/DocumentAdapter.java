package com.example.business.adapter;

import com.example.jooq.document.tables.daos.DocumentDao;
import com.example.jooq.document.tables.pojos.Document;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;

import java.time.OffsetDateTime;
import java.util.List;

import static com.example.jooq.document.tables.Document.DOCUMENT_;

@Singleton
public class DocumentAdapter {

    private final DSLContext dsl;
    private final DocumentDao dao;

    public DocumentAdapter(DSLContext dsl, DocumentDao dao) {
        this.dsl = dsl;
        this.dao = dao;
    }

    /**
     * Inserts the document and, when an embedding is available, sets it in the same transaction.
     * The embedding column is invisible to jOOQ codegen (see 18-document-vector.sql), so it is
     * written via a raw UPDATE with a pgvector literal.
     */
    @Transactional
    public Document create(Document document, String vectorLiteralOrNull) {
        dao.insert(document);
        if (vectorLiteralOrNull != null) {
            dsl.query("UPDATE document.document SET embedding = ?::vector WHERE id = ?",
                vectorLiteralOrNull, document.getId()).execute();
        }
        return document;
    }

    public Document getById(Long id) {
        return dao.fetchOneById(id);
    }

    public List<Document> keywordSearch(Condition condition, int page, int size) {
        return dsl.selectFrom(DOCUMENT_)
            .where(condition)
            .orderBy(DOCUMENT_.CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetchInto(Document.class);
    }

    public long countKeywordSearch(Condition condition) {
        return dsl.selectCount()
            .from(DOCUMENT_)
            .where(condition)
            .fetchOne(0, Long.class);
    }

    /**
     * Nearest-neighbour search: cosine distance (<=>) against the HNSW index,
     * similarity reported as 1 - distance (1 = identical meaning, 0 = unrelated).
     */
    public List<ContextRow> contextSearch(String vectorLiteral, int limit) {
        return dsl.resultQuery(
                "SELECT id, title, content_text, created_at, 1 - (embedding <=> ?::vector) AS similarity "
                    + "FROM document.document WHERE embedding IS NOT NULL "
                    + "ORDER BY embedding <=> ?::vector LIMIT ?",
                vectorLiteral, vectorLiteral, limit)
            .fetch(DocumentAdapter::toContextRow);
    }

    private static ContextRow toContextRow(Record record) {
        return new ContextRow(
            record.get("id", Long.class),
            record.get("title", String.class),
            record.get("content_text", String.class),
            record.get("created_at", OffsetDateTime.class),
            record.get("similarity", Double.class));
    }

    public record ContextRow(Long id, String title, String contentText, OffsetDateTime createdAt, Double similarity) {
    }
}
