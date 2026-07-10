package com.example.business.useCase;

import com.example.business.adapter.DocumentAdapter;
import com.example.jooq.document.tables.pojos.Document;
import com.example.util.search.TextSearchBuilder;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.List;

import static com.example.jooq.document.tables.Document.DOCUMENT_;

@Singleton
public class KeywordSearchDocuments {

    private final DocumentAdapter documentAdapter;

    public KeywordSearchDocuments(DocumentAdapter documentAdapter) {
        this.documentAdapter = documentAdapter;
    }

    @Transactional(readOnly = true)
    public Result execute(String keyword, Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? 50 : size;

        Condition condition = TextSearchBuilder.buildCondition(keyword,
            List.of(DOCUMENT_.TITLE, DOCUMENT_.CONTENT_TEXT));

        List<Document> documents = documentAdapter.keywordSearch(condition, safePage, safeSize);
        long totalSize = documentAdapter.countKeywordSearch(condition);
        int totalPages = (int) Math.ceil(totalSize / (double) safeSize);

        return new Result(documents, totalSize, totalPages);
    }

    public record Result(List<Document> content, long totalSize, int totalPages) {
    }
}
