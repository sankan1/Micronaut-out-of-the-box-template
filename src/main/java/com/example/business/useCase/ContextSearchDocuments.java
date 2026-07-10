package com.example.business.useCase;

import java.util.List;
import java.util.Optional;

import com.example.business.adapter.DocumentAdapter;
import com.example.business.adapter.DocumentAdapter.ContextRow;
import com.example.business.embedding.EmbeddingClient;

import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class ContextSearchDocuments {

    private final DocumentAdapter documentAdapter;
    private final EmbeddingClient embeddingClient;

    public ContextSearchDocuments(DocumentAdapter documentAdapter, EmbeddingClient embeddingClient) {
        this.documentAdapter = documentAdapter;
        this.embeddingClient = embeddingClient;
    }

    public List<ContextRow> execute(String query, Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 10 : Math.min(limit, 100);

        Optional<float[]> queryEmbedding = embeddingClient.embed(query);
        if (queryEmbedding.isEmpty()) {
            return List.of();
        }
        return search(EmbeddingClient.toVectorLiteral(queryEmbedding.get()), safeLimit);
    }

    @Transactional(readOnly = true)
    protected List<ContextRow> search(String vectorLiteral, int limit) {
        return documentAdapter.contextSearch(vectorLiteral, limit);
    }
}
