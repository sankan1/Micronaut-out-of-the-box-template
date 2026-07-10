package com.example.business.embedding;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Thin client for the Ollama embedding API (docker-compose service `ollama`).
 * Turns text into a dense vector via the configured multilingual model (bge-m3 by default).
 * Failures are returned as Optional.empty() so callers can degrade gracefully
 * (store the document without an embedding) instead of failing the whole request.
 */
@Singleton
public class EmbeddingClient {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingClient.class);

    private final HttpClient httpClient;
    private final String model;

    public EmbeddingClient(@Client(id = "embedding") HttpClient httpClient,
                           @Value("${embedding.model}") String model) {
        this.httpClient = httpClient;
        this.model = model;
    }

    public Optional<float[]> embed(String text) {
        try {
            EmbedResponse response = httpClient.toBlocking().retrieve(
                HttpRequest.POST("/api/embed", new EmbedRequest(model, List.of(text))),
                EmbedResponse.class);
            if (response == null || response.embeddings() == null || response.embeddings().isEmpty()) {
                LOG.warn("Embedding service returned no embeddings for model {}", model);
                return Optional.empty();
            }
            List<Float> values = response.embeddings().get(0);
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = values.get(i);
            }
            return Optional.of(vector);
        } catch (Exception e) {
            LOG.warn("Embedding service unavailable ({}); proceeding without embedding", e.getMessage());
            return Optional.empty();
        }
    }

    /** Serializes a vector into pgvector's literal format: [0.1,0.2,...] */
    public static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    @Serdeable
    public record EmbedRequest(String model, List<String> input) {
    }

    @Serdeable
    public record EmbedResponse(List<List<Float>> embeddings) {
    }
}
