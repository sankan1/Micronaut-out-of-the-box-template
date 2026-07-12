# Worked example: hybrid search (GIN + vectors in one endpoint), then full RAG (R + A + G)

Companion to [`search-explained.md`](search-explained.md) (the concepts) — this document is the *implementation* walkthrough, written in this repo's exact conventions so every snippet is copy-paste-ready. It builds two things:

1. **Part 1 — Hybrid search**: one API endpoint taking two string inputs, `keyword` and `contextSentence`, that combines both engines in a single SQL query: the GIN index *filters* (hard requirement: the word must be present) and the vector index *ranks* (order by meaning-closeness). This is the industry-standard "hybrid search" pattern.
2. **Part 2 — The A and G of RAG**: the existing context search is the **R**etrieval step; here we add **A**ugmentation (build a prompt around the retrieved documents) and **G**eneration (a local Ollama chat model writes a grounded answer with sources) — a complete `POST /documents/ask` endpoint, still 100% local and free.

The document is self-contained: **Part 0** builds the foundation both features stand on (the pgvector-capable Postgres, the Ollama embedding service, the table + both indexes, and how rows get vectorized), matching what is already implemented in this repo — read it as either "how the existing pieces fit" or "how to replicate this for a new table."

---

## Part 0 — Foundation: the vector-capable database and vectorizing table rows

### 0.1 Postgres must be a pgvector image — plain `postgres` will not work

The `vector` data type, the `<=>` distance operator, and the HNSW index all come from the **pgvector extension**, which the stock `postgres` image does not ship. The `pgvector/pgvector` images are the official Postgres images with the extension pre-installed — a drop-in swap (same data directory layout, an existing Postgres-17 volume survives the change).

Three places in this repo point at the image, all already done:

```bash
# .env - referenced by docker-compose
PG_IMAGE=pgvector/pgvector:pg17
```

```yaml
# docker-compose.yaml
services:
  local-db:
    image: ${PG_IMAGE}     # was: postgres:${PG_VERSION}
```

```yaml
# .github/workflows/backend-ci.yml - CI runs the same migrations, so same requirement
services:
  postgres:
    image: pgvector/pgvector:pg17
```

Installing the extension is then a one-line migration (§0.3) — `CREATE EXTENSION` only *activates* what the image already contains; on a plain image the same statement fails with "extension \"vector\" is not available", which is the error you'll see anywhere this image swap was forgotten (e.g. a production database — managed Postgres services like RDS/Cloud SQL/OCI have pgvector available behind the same `CREATE EXTENSION` call).

### 0.2 The embedding service: Ollama + a multilingual model

Vectors don't compute themselves — an embedding model turns text into them, both at row-insert time and at query time. This repo runs the model locally via Ollama (free, offline, no API keys):

```yaml
# docker-compose.yaml
  ollama:
    image: ollama/ollama
    ports:
      - "11434:11434"
    volumes:
      - ollama-models:/root/.ollama    # + add ollama-models to the volumes: block
```

```bash
# one-time model download (~1.2GB); bge-m3 = multilingual (handles Estonian), 1024 dimensions
docker exec -it micronaut-out-of-the-box-template-ollama-1 ollama pull bge-m3
```

```bash
# .env
EMBEDDING_URL=http://localhost:11434
EMBEDDING_MODEL=bge-m3
```

```yaml
# application.yml - service-id client so the timeout is configurable;
# 60s because Ollama unloads idle models and the next call pays a cold re-load
micronaut:
  http:
    services:
      embedding:
        url: "${EMBEDDING_URL:`http://localhost:11434`}"
        read-timeout: 60s

embedding:
  model: ${EMBEDDING_MODEL:bge-m3}
```

**The model's output size dictates the column type**: `bge-m3` emits 1024 floats → `vector(1024)` in §0.3. Swap the model and you must change the dimension *and re-embed every stored row* — embeddings from different models are not comparable.

### 0.3 Migrations: the table, both indexes, and the codegen trick

Three Liquibase files (already in `database/src/main/resources/changelog/structure/`):

```sql
-- 16-document-schema.sql
--changeset sander:add-document-schema
CREATE SCHEMA document;
```

```sql
-- 17-document-table.sql  (NO context attribute - runs everywhere, jOOQ generates it)
--changeset sander:add-document-table
CREATE TABLE document.document (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    title VARCHAR(255) NOT NULL,
    content JSONB NOT NULL,        -- {"title": ..., "paragraphs": [...]} - parseable back to text
    content_text TEXT NOT NULL,    -- flattened plain text: GIN target, embedding source, preview
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT document__id__pkey PRIMARY KEY (id)
);

--changeset sander:add-document-search-index
CREATE INDEX document__combined_search__fts_idx ON document.document USING GIN ((
    to_tsvector('simple', coalesce(title, '')) ||
    to_tsvector('simple', coalesce(content_text, ''))
));
```

```sql
-- 18-document-vector.sql  (EVERY changeset gated with context:!jooq - see below)
--changeset sander:add-pgvector-extension context:!jooq
CREATE EXTENSION IF NOT EXISTS vector;

--changeset sander:add-document-embedding-column context:!jooq
ALTER TABLE document.document ADD COLUMN embedding vector(1024);

--changeset sander:add-document-embedding-index context:!jooq
CREATE INDEX document__embedding__hnsw_idx ON document.document
    USING hnsw (embedding vector_cosine_ops);
```

**Why the `context:!jooq` gating** — the repo's jOOQ codegen (`generateJooq` → `:database:dump`) replays all migrations against a throwaway *plain* Postgres Testcontainer, run with `--contexts=jooq`. Liquibase semantics: a `context:!jooq` changeset is skipped under `--contexts=jooq` but runs under a plain `update` (no context filter) — so the codegen container never needs pgvector, and jOOQ never generates the `embedding` column. Real databases (dev/CI/prod, §0.1 image) get everything. The trade-off: the embedding column lives outside the type-safe layer and is only touched via raw SQL (`?::vector` binds), which is exactly what `DocumentAdapter` does.

One `build.gradle` line makes codegen pick up the new schema:

```groovy
schemata {
    // ...existing schemas...
    schema { inputSchema = 'document' }
}
```

Then: `./gradlew :database:update generateJooq compileJava`.

### 0.4 The embedding client

Full file (already at `src/main/java/com/example/business/embedding/EmbeddingClient.java`): wraps Ollama's `POST /api/embed`, returns `Optional.empty()` on any failure so callers can degrade instead of erroring, and serializes vectors into pgvector's literal format (`[0.1,0.2,...]`):

```java
@Singleton
public class EmbeddingClient {

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

    public static String toVectorLiteral(float[] vector) { /* "[0.1,0.2,...]" */ }

    @Serdeable public record EmbedRequest(String model, List<String> input) {}
    @Serdeable public record EmbedResponse(List<List<Float>> embeddings) {}
}
```

⚠️ Any controller whose request path reaches `toBlocking()` must be `@ExecuteOn(TaskExecutors.BLOCKING)` — Micronaut hard-rejects blocking HTTP calls on Netty event-loop threads (a bug this repo hit for real; `DocumentController` carries the annotation for exactly this reason).

### 0.5 Vectorizing table rows

**New rows** are embedded at insert time — the flow in `CreateDocument` + `DocumentAdapter.create`:

1. Flatten the pasted text (`content_text`), embed `title + "\n\n" + contentText` via `EmbeddingClient` — *before* any transaction, since it's a remote HTTP call that shouldn't hold a DB connection hostage.
2. Insert the row through the generated jOOQ DAO (which knows nothing about the embedding column).
3. In the same transaction, set the vector with raw SQL:

```java
@Transactional
public Document create(Document document, String vectorLiteralOrNull) {
    dao.insert(document);
    if (vectorLiteralOrNull != null) {
        dsl.query("UPDATE document.document SET embedding = ?::vector WHERE id = ?",
            vectorLiteralOrNull, document.getId()).execute();
    }
    return document;
}
```

If the embedding service is down, the row is stored with `embedding = NULL`: still keyword-searchable, invisible to vector search, flagged `embedded:false` in the API response — uploads never fail because a sidecar is offline.

**Existing rows** (backfill — e.g. after adding the column to a table that already has data, or after a model swap): loop over rows where `embedding IS NULL`, embed `content_text`, and run the same `UPDATE`. Small datasets: a throwaway script against the API or DB is fine. Larger ones: batch it (Ollama's `/api/embed` accepts a *list* of inputs per call — `EmbedRequest.input` is already `List<String>`) and process a few hundred rows per transaction. There is deliberately no automatic backfill job in this repo — for a template, an explicit one-off step beats a hidden background process.

---

## Part 1 — Hybrid search: `keyword` filters, `contextSentence` ranks

### 1.1 Why combine them at all?

Each engine alone has a blind spot:

- Pure **keyword** search can't rank by relevance of *meaning* — every document containing "leping" is an equal hit, whether it's about insurance contracts or rental agreements.
- Pure **context** search can't *guarantee* a term is present — searching "kindlustusleping" semantically might top-rank a document that discusses insurance at length but never uses the exact word you need for, say, a legal citation.

Hybrid = precision of the first, intelligence of the second: *"only documents containing `keyword`, best-matching `contextSentence` first."*

### 1.2 No new migration needed

Both indexes already exist (`17-document-table.sql` GIN, `18-document-vector.sql` HNSW). This whole feature is query-side composition — a useful lesson in itself: once a table has both a tsvector GIN index and an embedding column, any mix of filtering/ranking is just SQL.

### 1.3 OpenAPI spec (`openapi/src/main/resources/swagger/openapi.yml`)

Add under `paths:` (then regenerate: `./gradlew :openapi:generateServerOpenApiModels :openapi:generateServerOpenApiApis`):

```yaml
  /documents/hybrid-search:
    get:
      tags: [documents]
      operationId: hybridSearchDocuments
      description: >
        Combined search: documents must contain the keyword (GIN full-text filter),
        results are ordered by semantic similarity to contextSentence (vector ranking).
      parameters:
        - { name: keyword, in: query, required: true, schema: { type: string, minLength: 2 } }
        - { name: contextSentence, in: query, required: true, schema: { type: string, minLength: 2 } }
        - { name: limit, in: query, schema: { type: integer, default: 10 } }
      responses:
        '200':
          description: Keyword-filtered documents, ranked by semantic similarity
          content:
            application/json:
              schema:
                type: array
                items: { $ref: '#/components/schemas/Document' }
```

The existing `Document` schema already carries `similarity`, so no new model is needed.

### 1.4 Adapter method (`DocumentAdapter`)

The interesting part: composing a **type-safe jOOQ `Condition`** (from `TextSearchBuilder`, which the GIN index accelerates) with **raw vector SQL fragments** (because the `embedding` column is deliberately invisible to jOOQ codegen — see the `context:!jooq` note in `backend-tech-solution.md` §3). jOOQ's plain-SQL templating (`DSL.field`/`DSL.condition` with `{0}` placeholders) lets the two worlds meet in one query:

```java
// add to DocumentAdapter
import org.jooq.Field;
import org.jooq.impl.DSL;

/**
 * Hybrid search: `condition` (tsvector match, GIN-accelerated) filters,
 * cosine distance to the query vector ranks. Documents without an embedding
 * are excluded - they cannot be ranked by meaning.
 */
public List<ContextRow> hybridSearch(Condition keywordCondition, String vectorLiteral, int limit) {
    Field<Double> similarity =
        DSL.field("1 - (embedding <=> {0}::vector)", Double.class, DSL.val(vectorLiteral));

    return dsl.select(
            DOCUMENT_.ID, DOCUMENT_.TITLE, DOCUMENT_.CONTENT_TEXT, DOCUMENT_.CREATED_AT, similarity)
        .from(DOCUMENT_)
        .where(keywordCondition)
        .and(DSL.condition("embedding IS NOT NULL"))
        .orderBy(DSL.field("embedding <=> {0}::vector", DSL.val(vectorLiteral)).asc())
        .limit(limit)
        .fetch(record -> new ContextRow(
            record.get(DOCUMENT_.ID),
            record.get(DOCUMENT_.TITLE),
            record.get(DOCUMENT_.CONTENT_TEXT),
            record.get(DOCUMENT_.CREATED_AT),
            record.get(similarity)));
}
```

Note the ORDER BY uses the raw distance (`<=>` ascending = closest first) — the same expression form as the HNSW index (`vector_cosine_ops`), which is what allows Postgres to use the index for the ordering instead of sorting every filtered row.

### 1.5 Use case

Same shape as `ContextSearchDocuments`: embed *outside* any transaction (it's a remote HTTP call), degrade to an empty result if the embedding service is down:

```java
package com.example.business.useCase;

import com.example.business.adapter.DocumentAdapter;
import com.example.business.adapter.DocumentAdapter.ContextRow;
import com.example.business.embedding.EmbeddingClient;
import com.example.util.search.TextSearchBuilder;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.List;
import java.util.Optional;

import static com.example.jooq.document.tables.Document.DOCUMENT_;

@Singleton
public class HybridSearchDocuments {

    private final DocumentAdapter documentAdapter;
    private final EmbeddingClient embeddingClient;

    public HybridSearchDocuments(DocumentAdapter documentAdapter, EmbeddingClient embeddingClient) {
        this.documentAdapter = documentAdapter;
        this.embeddingClient = embeddingClient;
    }

    public List<ContextRow> execute(String keyword, String contextSentence, Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 10 : Math.min(limit, 100);

        Condition keywordCondition = TextSearchBuilder.buildCondition(keyword,
            List.of(DOCUMENT_.TITLE, DOCUMENT_.CONTENT_TEXT));

        Optional<float[]> queryEmbedding = embeddingClient.embed(contextSentence);
        if (queryEmbedding.isEmpty()) {
            return List.of();   // no embedding service -> no ranking possible
        }
        return search(keywordCondition, EmbeddingClient.toVectorLiteral(queryEmbedding.get()), safeLimit);
    }

    @Transactional(readOnly = true)
    protected List<ContextRow> search(Condition keywordCondition, String vectorLiteral, int limit) {
        return documentAdapter.hybridSearch(keywordCondition, vectorLiteral, limit);
    }
}
```

(Variant worth knowing: if you want *graceful* rather than empty behaviour when Ollama is down, fall back to `documentAdapter.keywordSearch(keywordCondition, 0, safeLimit)` — hybrid degrades to plain keyword search instead of nothing.)

### 1.6 Controller method

`DocumentController` already implements `DocumentsApi` and — crucially — already carries `@ExecuteOn(TaskExecutors.BLOCKING)` at class level. That annotation is not optional boilerplate: this endpoint does a blocking HTTP call (embedding) plus blocking JDBC, and Micronaut *hard-fails* blocking HTTP client calls made on a Netty event-loop thread (a bug this repo hit in practice — see `backend-tech-solution.md` §5).

```java
// add to DocumentController (interface method comes from the regenerated DocumentsApi)
@Override
public HttpResponse<@NotNull List<@Valid Document>> hybridSearchDocuments(
        String keyword, String contextSentence, Integer limit) {
    return HttpResponse.ok(DocumentMapper.mapContextRows(
        hybridSearchDocuments.execute(keyword, contextSentence, limit)));
}
```

(Plus the constructor injection of a `HybridSearchDocuments hybridSearchDocuments` field, same pattern as the other four use cases.)

### 1.7 What it returns, on the wolf/lamb corpus

Corpus: `"kriimsilm sööb utte"`, `"hunt sööb lammast"`, `"võsavillem näksib voona"`, plus the unrelated insurance memo.

```
GET /documents/hybrid-search?keyword=sööb&contextSentence=hunt murrab lambatalle

[
  { "title": "Hundijutt",  "text": "hunt sööb lammast",   "similarity": 0.87 },
  { "title": "Rebasejutt", "text": "kriimsilm sööb utte", "similarity": 0.55 }
]
```

- "võsavillem näksib voona" is **excluded** despite being semantically close — it fails the keyword filter (no word `sööb`; it uses `näksib`). That's the "hard guarantee" half doing its job.
- The insurance memo is excluded by the keyword filter too (and would rank last anyway).
- Among the survivors, ranking comes from meaning-closeness to `contextSentence`, not from where the keyword appears.

That asymmetry — filter vs. rank — is the entire design: **`keyword` decides *whether* a document appears; `contextSentence` decides *where*.**

---

## Part 2 — Completing RAG: the A and the G

The context search built earlier is the **R** (Retrieval). RAG adds two steps on top:

```
question ──► [R] embed question, fetch top-K similar documents      (already built)
             [A] build a prompt that contains those documents        (this section)
             [G] a chat LLM answers *from the prompt*, with sources  (this section)
```

Everything below stays local and free: Ollama already runs for embeddings; the same server can host a small chat model.

### 2.1 Infrastructure: one more model, zero new services

```bash
docker exec -it micronaut-out-of-the-box-template-ollama-1 ollama pull qwen2.5:3b
```

`qwen2.5:3b` (~1.9GB) is a reasonable CPU-friendly choice with decent multilingual coverage (relevant for Estonian documents); `llama3.2:3b` is a common alternative. Config follows the repo's existing pattern — `.env`:

```
CHAT_MODEL=qwen2.5:3b
```

`application.yml` — note generation is *much* slower than embedding on CPU, so the timeout is generous, and it gets its own service id even though the URL is the same Ollama instance (so the two timeouts stay independent):

```yaml
micronaut:
  http:
    services:
      chat:
        url: "${EMBEDDING_URL:`http://localhost:11434`}"
        read-timeout: 300s   # CPU generation of a few hundred tokens takes tens of seconds

chat:
  model: ${CHAT_MODEL:qwen2.5:3b}
```

### 2.2 The G plumbing: `ChatClient`

Sibling of `EmbeddingClient`, calling Ollama's `/api/chat` (non-streaming for simplicity):

```java
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

@Singleton
public class ChatClient {

    private static final Logger LOG = LoggerFactory.getLogger(ChatClient.class);

    private final HttpClient httpClient;
    private final String model;

    public ChatClient(@Client(id = "chat") HttpClient httpClient,
                      @Value("${chat.model}") String model) {
        this.httpClient = httpClient;
        this.model = model;
    }

    public Optional<String> chat(String systemPrompt, String userPrompt) {
        try {
            ChatResponse response = httpClient.toBlocking().retrieve(
                HttpRequest.POST("/api/chat", new ChatRequest(model,
                    List.of(new Message("system", systemPrompt), new Message("user", userPrompt)),
                    false)),
                ChatResponse.class);
            return Optional.ofNullable(response)
                .map(ChatResponse::message)
                .map(Message::content);
        } catch (Exception e) {
            LOG.warn("Chat service unavailable ({})", e.getMessage());
            return Optional.empty();
        }
    }

    @Serdeable
    public record ChatRequest(String model, List<Message> messages, boolean stream) {
    }

    @Serdeable
    public record Message(String role, String content) {
    }

    @Serdeable
    public record ChatResponse(Message message) {
    }
}
```

### 2.3 The A: prompt assembly — this is the whole trick

Augmentation sounds grand; it is string concatenation with discipline. The rules that matter:

1. **Number the sources** so the model can cite them and you can map citations back to documents.
2. **Fence the model in**: instruct it to answer *only* from the provided documents and to say so when they don't contain the answer — this is the main defence against hallucinated answers.
3. **Threshold retrieval**: don't stuff barely-related documents into the prompt; below ~0.4 cosine similarity you're mostly adding noise for the model to get distracted by.

```java
package com.example.business.useCase;

import com.example.business.adapter.DocumentAdapter.ContextRow;
import com.example.business.embedding.ChatClient;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class AskDocuments {

    private static final double MIN_SIMILARITY = 0.35;
    private static final int TOP_K = 4;

    private static final String SYSTEM_PROMPT = """
        You are a careful assistant answering questions about the user's private document collection.
        Answer ONLY using the numbered source documents provided. Cite sources as [1], [2] etc.
        If the documents do not contain the answer, say exactly that - do not use outside knowledge.
        Answer in the same language as the question.
        """;

    private final ContextSearchDocuments contextSearchDocuments;
    private final ChatClient chatClient;

    public AskDocuments(ContextSearchDocuments contextSearchDocuments, ChatClient chatClient) {
        this.contextSearchDocuments = contextSearchDocuments;
        this.chatClient = chatClient;
    }

    public Result execute(String question) {
        // R - retrieval (reuses the existing use case wholesale)
        List<ContextRow> retrieved = contextSearchDocuments.execute(question, TOP_K).stream()
            .filter(row -> row.similarity() != null && row.similarity() >= MIN_SIMILARITY)
            .toList();

        if (retrieved.isEmpty()) {
            return new Result("No sufficiently relevant documents were found for this question.", List.of());
        }

        // A - augmentation: the question wrapped around numbered source excerpts
        String sourcesBlock = buildSourcesBlock(retrieved);
        String userPrompt = "Source documents:\n\n" + sourcesBlock + "\nQuestion: " + question;

        // G - generation
        String answer = chatClient.chat(SYSTEM_PROMPT, userPrompt)
            .orElse("The answer service is currently unavailable - here are the most relevant documents instead.");

        return new Result(answer, retrieved);
    }

    private static String buildSourcesBlock(List<ContextRow> rows) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            ContextRow row = rows.get(i);
            sb.append('[').append(i + 1).append("] ").append(row.title()).append('\n')
              .append(row.contentText()).append("\n\n");
        }
        return sb.toString();
    }

    public record Result(String answer, List<ContextRow> sources) {
    }
}
```

For long documents you would truncate or select the best *chunk* per document here — with this app's short documents, whole texts fit comfortably in the model's context window, so chunking is deliberately out of scope (it's the first thing to add when documents grow past a few pages).

### 2.4 The endpoint

OpenAPI:

```yaml
  /documents/ask:
    post:
      tags: [documents]
      operationId: askDocuments
      description: "RAG: retrieves the most relevant documents and generates a grounded, cited answer"
      requestBody:
        content:
          application/json:
            schema: { $ref: '#/components/schemas/AskRequest' }
      responses:
        '200':
          description: Generated answer plus the source documents it was grounded in
          content:
            application/json:
              schema: { $ref: '#/components/schemas/AskResponse' }
```

```yaml
    AskRequest:
      type: object
      required: [question]
      properties:
        question: { type: string, minLength: 3 }
    AskResponse:
      type: object
      required: [answer]
      properties:
        answer: { type: string }
        sources:
          type: array
          items: { $ref: '#/components/schemas/Document' }
```

Controller addition (again: the class-level `@ExecuteOn(TaskExecutors.BLOCKING)` is what makes the long blocking chat call legal):

```java
@Override
public HttpResponse<@Valid AskResponse> askDocuments(AskRequest askRequest) {
    var result = askDocuments.execute(askRequest.getQuestion());
    return HttpResponse.ok(new AskResponse(result.answer())
        .sources(DocumentMapper.mapContextRows(result.sources())));
}
```

### 2.5 What a full round trip looks like

```
POST /documents/ask
{ "question": "Kes sööb lambaid minu juttudes?" }
```

1. **R**: the question is embedded (bge-m3); nearest neighbours: Hundijutt (0.71), Rebasejutt (0.52), Võsavillemi lugu (0.49) — insurance memo falls under the 0.35 threshold and never reaches the prompt.
2. **A**: prompt assembled — system instructions + three numbered sources + the question.
3. **G**: the local model answers, e.g.:

```json
{
  "answer": "Sinu juttudes söövad lambaid ja teisi väikeloomi kiskjad: hunt sööb lammast [2], kriimsilm sööb utte [1] ning võsavillem näksib voona [3].",
  "sources": [
    { "id": 2, "title": "Rebasejutt",       "similarity": 0.52, ... },
    { "id": 3, "title": "Hundijutt",        "similarity": 0.71, ... },
    { "id": 4, "title": "Võsavillemi lugu", "similarity": 0.49, ... }
  ]
}
```

### 2.5b Controller wiring (the part every snippet above assumes)

Both new controller methods live in the existing `DocumentController` — the overridden methods shown in §1.6 and §2.4 additionally need their use cases injected, same constructor pattern as everything else in this repo:

```java
@ExecuteOn(TaskExecutors.BLOCKING)   // already present - do not remove, see §1.6
@Controller
public class DocumentController implements DocumentsApi {

    private final CreateDocument createDocument;
    private final GetDocument getDocument;
    private final KeywordSearchDocuments keywordSearchDocuments;
    private final ContextSearchDocuments contextSearchDocuments;
    private final HybridSearchDocuments hybridSearchDocuments;   // NEW (Part 1)
    private final AskDocuments askDocuments;                     // NEW (Part 2)

    public DocumentController(
            CreateDocument createDocument,
            GetDocument getDocument,
            KeywordSearchDocuments keywordSearchDocuments,
            ContextSearchDocuments contextSearchDocuments,
            HybridSearchDocuments hybridSearchDocuments,
            AskDocuments askDocuments) {
        this.createDocument = createDocument;
        this.getDocument = getDocument;
        this.keywordSearchDocuments = keywordSearchDocuments;
        this.contextSearchDocuments = contextSearchDocuments;
        this.hybridSearchDocuments = hybridSearchDocuments;
        this.askDocuments = askDocuments;
    }
    // ... existing methods + the two new @Override methods from §1.6 / §2.4
}
```

Micronaut wires the beans automatically — no factory registration needed for `@Singleton` use cases (only jOOQ DAOs go through `DaoFactory`, and neither new feature adds a DAO).

### 2.6 Honest limitations to keep in mind

- **The G step is only as good as the R step** — if retrieval misses the right document, the model *cannot* answer correctly, no matter how good it is. Retrieval quality (embedding model, thresholds, chunking) is where RAG systems are actually won or lost.
- **Grounding instructions reduce, not eliminate, hallucination.** A 3B model will occasionally blend in outside knowledge or over-summarize. Showing the sources next to the answer (as this design does) is the practical mitigation: the user can always check.
- **CPU latency is real**: a few hundred generated tokens takes tens of seconds on CPU. For interactive use, switch `stream: true` and proxy the token stream (Micronaut supports streaming responses) — kept out of this example for clarity.
- **Threshold + top-K are product decisions**, not constants of nature: higher threshold = fewer "I found nothing" false-positives but more false "no relevant documents"; tune against real usage.

---

## Appendix: complete checklist of every file touched

Everything the features require, in implementation order. Files marked *(new)* are created; everything else is an edit to an existing file. Parts 1 and 2 need no database migrations — they run entirely on Part 0's schema and indexes.

**Part 0 — foundation** (all already implemented in this repo; listed for replication on a new table/project)

| # | File | Change |
|---|---|---|
| 0a | `.env` | `PG_IMAGE=pgvector/pgvector:pg17`, `EMBEDDING_URL`, `EMBEDDING_MODEL` (§0.1, §0.2) |
| 0b | `docker-compose.yaml` | `local-db` image → `${PG_IMAGE}`; add `ollama` service + `ollama-models` volume (§0.1, §0.2) |
| 0c | `.github/workflows/backend-ci.yml` | Postgres service image → `pgvector/pgvector:pg17` (§0.1) |
| 0d | — | `docker compose up -d local-db ollama` + `ollama pull bge-m3` (§0.2) |
| 0e | `src/main/resources/application.yml` | `micronaut.http.services.embedding` (url + 60s timeout) + `embedding.model` (§0.2) |
| 0f | `database/.../structure/16-document-schema.sql` *(new)* | Schema (§0.3) |
| 0g | `database/.../structure/17-document-table.sql` *(new)* | Table + GIN index — no context attribute (§0.3) |
| 0h | `database/.../structure/18-document-vector.sql` *(new)* | Extension + `vector(1024)` column + HNSW index, **all `context:!jooq`** (§0.3) |
| 0i | `build.gradle` | `schema { inputSchema = 'document' }` in the jOOQ block (§0.3) |
| 0j | — | `./gradlew :database:update generateJooq compileJava` (§0.3) |
| 0k | `src/main/java/com/example/business/embedding/EmbeddingClient.java` *(new)* | Full file in §0.4 |
| 0l | `src/main/java/com/example/business/adapter/DocumentAdapter.java` *(new)* | Insert + raw `?::vector` UPDATE (§0.5), searches (§1.4 adds hybrid) |
| 0m | `src/main/java/com/example/config/DaoFactory.java` | Register `DocumentDao` bean |
| 0n | Use cases + `DocumentMapper` + `DocumentController` *(new)* | Create/get/keyword/context per the repo's standard layering; controller is `@ExecuteOn(TaskExecutors.BLOCKING)` (§0.4 warning) |

**Part 1 — hybrid search**

| # | File | Change |
|---|---|---|
| 1 | `openapi/src/main/resources/swagger/openapi.yml` | Add `/documents/hybrid-search` path (§1.3) |
| 2 | — | Run `./gradlew :openapi:generateServerOpenApiModels :openapi:generateServerOpenApiApis` → regenerates `DocumentsApi` with the new interface method |
| 3 | `src/main/java/com/example/business/adapter/DocumentAdapter.java` | Add `hybridSearch(...)` method (§1.4) |
| 4 | `src/main/java/com/example/business/useCase/HybridSearchDocuments.java` *(new)* | Full file in §1.5 |
| 5 | `src/main/java/com/example/controller/DocumentController.java` | Inject `HybridSearchDocuments` (§2.5b) + add the `@Override` method (§1.6) |

**Part 2 — RAG (A + G)**

| # | File | Change |
|---|---|---|
| 6 | — | `docker exec -it micronaut-out-of-the-box-template-ollama-1 ollama pull qwen2.5:3b` (one-time, ~1.9GB) |
| 7 | `.env` | Add `CHAT_MODEL=qwen2.5:3b` (§2.1) |
| 8 | `src/main/resources/application.yml` | Add `micronaut.http.services.chat` (url + 300s read-timeout) and `chat.model` (§2.1) |
| 9 | `openapi/src/main/resources/swagger/openapi.yml` | Add `/documents/ask` path + `AskRequest`/`AskResponse` schemas (§2.4), then regenerate as in step 2 |
| 10 | `src/main/java/com/example/business/embedding/ChatClient.java` *(new)* | Full file in §2.2 |
| 11 | `src/main/java/com/example/business/useCase/AskDocuments.java` *(new)* | Full file in §2.3 |
| 12 | `src/main/java/com/example/controller/DocumentController.java` | Inject `AskDocuments` (§2.5b) + add the `@Override` method (§2.4) |

**Optional frontend follow-through** (not required — both endpoints are fully usable via curl/HTTP as-is; listed because keeping the two repos' specs in sync is this project's convention):

| # | File (Vue repo) | Change |
|---|---|---|
| 13 | `openapi/openapi.yml` | Copy the updated spec over from the backend repo |
| 14 | — | `npm run generate:openapi` → Orval regenerates `hybridSearchDocuments()` / `askDocuments()` client functions under `src/generated/api/documents/` |
| 15 | `src/pages/DocumentSearch.vue` + `src/stores/documents.store.ts` | Optionally add a hybrid mode (two inputs, one search button) and/or an "Ask" box rendering `answer` + `sources` |

**Verification** (mirrors how the document feature itself was verified):

```bash
# hybrid: keyword filters, context ranks - expect Hundijutt + Rebasejutt, NOT Võsavillemi lugu
curl "http://localhost:8080/documents/hybrid-search?keyword=sööb&contextSentence=hunt%20murrab%20lambatalle"

# RAG round trip - expect a cited answer + sources array
curl -X POST -H "Content-Type: application/json" \
  -d '{"question":"Kes sööb lambaid minu juttudes?"}' \
  http://localhost:8080/documents/ask
```

(Estonian characters from a Windows shell can get mangled by console encoding — if uploads/queries look corrupted, send the requests from a small Python script with explicit UTF-8, which is how this repo's own verification was done.)
