package com.example.controller;

import com.example.business.mapper.DocumentMapper;
import com.example.business.useCase.ContextSearchDocuments;
import com.example.business.useCase.CreateDocument;
import com.example.business.useCase.GetDocument;
import com.example.business.useCase.KeywordSearchDocuments;
import com.example.openapi.api.DocumentsApi;
import com.example.openapi.model.Document;
import com.example.openapi.model.DocumentCreateRequest;
import com.example.openapi.model.DocumentsResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// Off the Netty event loop: these endpoints do blocking work (JDBC via jOOQ and the
// blocking HTTP call to the embedding service), which must not run on event-loop threads.
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller
public class DocumentController implements DocumentsApi {

    private final CreateDocument createDocument;
    private final GetDocument getDocument;
    private final KeywordSearchDocuments keywordSearchDocuments;
    private final ContextSearchDocuments contextSearchDocuments;

    public DocumentController(
            CreateDocument createDocument,
            GetDocument getDocument,
            KeywordSearchDocuments keywordSearchDocuments,
            ContextSearchDocuments contextSearchDocuments
    ) {
        this.createDocument = createDocument;
        this.getDocument = getDocument;
        this.keywordSearchDocuments = keywordSearchDocuments;
        this.contextSearchDocuments = contextSearchDocuments;
    }

    @Override
    public HttpResponse<@Valid Document> createDocument(DocumentCreateRequest documentCreateRequest) {
        var result = createDocument.execute(documentCreateRequest);
        return HttpResponse.ok(DocumentMapper.mapToDocument(result.document()).embedded(result.embedded()));
    }

    @Override
    public HttpResponse<@Valid Document> getDocument(Integer id) {
        var document = getDocument.execute(id.longValue());
        if (document == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(DocumentMapper.mapToDocument(document));
    }

    @Override
    public HttpResponse<@Valid DocumentsResponse> searchDocuments(String keyword, Integer page, Integer size) {
        var result = keywordSearchDocuments.execute(keyword, page, size);
        DocumentsResponse response = new DocumentsResponse()
            .content(DocumentMapper.mapToDocuments(result.content()))
            .totalSize((int) result.totalSize())
            .totalPages(result.totalPages());
        return HttpResponse.ok(response);
    }

    @Override
    public HttpResponse<@NotNull List<@Valid Document>> contextSearchDocuments(String query, Integer limit) {
        return HttpResponse.ok(DocumentMapper.mapContextRows(contextSearchDocuments.execute(query, limit)));
    }
}
