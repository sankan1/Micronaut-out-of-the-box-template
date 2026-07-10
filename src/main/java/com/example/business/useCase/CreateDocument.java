package com.example.business.useCase;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.jooq.JSONB;

import com.example.business.adapter.DocumentAdapter;
import com.example.business.embedding.EmbeddingClient;
import com.example.jooq.document.tables.pojos.Document;
import com.example.openapi.model.DocumentCreateRequest;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

@Singleton
public class CreateDocument {

    private final DocumentAdapter documentAdapter;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;

    public CreateDocument(DocumentAdapter documentAdapter, EmbeddingClient embeddingClient, ObjectMapper objectMapper) {
        this.documentAdapter = documentAdapter;
        this.embeddingClient = embeddingClient;
        this.objectMapper = objectMapper;
    }

    /**
     * The embedding is fetched before the transaction (it's a remote HTTP call), and its absence
     * is not an error: an un-embedded document is still stored and keyword-searchable, it just
     * won't appear in context-search results (flagged via Result.embedded).
     */
    public Result execute(DocumentCreateRequest request) {
        List<String> paragraphs = splitIntoParagraphs(request.getText());
        String contentText = String.join("\n\n", paragraphs);

        Optional<float[]> embedding = embeddingClient.embed(request.getTitle() + "\n\n" + contentText);

        Document document = new Document()
            .setTitle(request.getTitle())
            .setContent(toContentJson(request.getTitle(), paragraphs))
            .setContentText(contentText);

        Document created = documentAdapter.create(
            document,
            embedding.map(EmbeddingClient::toVectorLiteral).orElse(null));

        return new Result(created, embedding.isPresent());
    }

    // this needs some of that magic, not sure if it can be done with a regex, but this is good enough for now
    private static List<String> splitIntoParagraphs(String text) {
        return Arrays.stream(text.split("\\r?\\n\\s*\\r?\\n"))
            .map(String::trim)
            .filter(paragraph -> !paragraph.isEmpty())
            .toList();
    }

    private JSONB toContentJson(String title, List<String> paragraphs) {
        try {
            return JSONB.valueOf(objectMapper.writeValueAsString(new DocumentContent(title, paragraphs)));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize document content", e);
        }
    }

    @Serdeable
    public record DocumentContent(String title, List<String> paragraphs) {
    }

    public record Result(Document document, boolean embedded) {
    }
}
