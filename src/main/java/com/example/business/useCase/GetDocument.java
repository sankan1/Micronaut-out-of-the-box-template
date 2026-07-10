package com.example.business.useCase;

import com.example.business.adapter.DocumentAdapter;
import com.example.jooq.document.tables.pojos.Document;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class GetDocument {

    private final DocumentAdapter documentAdapter;

    public GetDocument(DocumentAdapter documentAdapter) {
        this.documentAdapter = documentAdapter;
    }

    @Transactional(readOnly = true)
    public Document execute(Long id) {
        return documentAdapter.getById(id);
    }
}
