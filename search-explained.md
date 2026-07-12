# How search works in this app: GIN indexes, vector embeddings, and where RAG fits

This app has two completely different search engines sitting side by side, and they answer two different questions:

| Engine | Question it answers | Example |
|---|---|---|
| **Keyword search** (GIN full-text index) | *"Which documents contain these exact words?"* | `lammast` finds "hunt sööb **lammast**" — and nothing else |
| **Context search** (vector embeddings, pgvector) | *"Which documents mean roughly the same thing?"* | `hunt sööb lammast` also finds "kriimsilm sööb utte" and "võsavillem näksib voona" |

Both are real, production-grade techniques — this document explains how each one works, first in plain language, then with the technical detail underneath.

---

## 1. Keyword search: GIN indexes and `tsvector`

### In plain language

Imagine a librarian who reads every book in the library once and builds a card catalogue: one card per *word*, and on each card, a list of every book that contains that word. When you ask "which books mention *lammas*?", she doesn't re-read a single book — she pulls the *lammas* card and reads the list off it. That card catalogue is a GIN index.

Two important consequences:

1. **It's extremely fast**, no matter how many documents you have — looking up a card takes the same time whether the library has 100 books or 10 million.
2. **It only knows words, not meanings.** There is no card that connects *hunt* (wolf) to *kriimsilm* or *võsavillem* (folk names for fox and wolf). If the word isn't literally in the document, the card catalogue cannot find it. This is the fundamental limitation that context search (§2) exists to fix.

### The technical detail

- **`tsvector`** is PostgreSQL's "list of words in this text" data type. `to_tsvector('simple', 'hunt sööb lammast')` produces something like `'hunt':1 'lammast':3 'sööb':2` — each word (lexeme) with its position. The `'simple'` configuration used in this app just lowercases and splits; language-specific configurations (like `'english'`) additionally *stem* words (`running` → `run`). There's no built-in Estonian stemmer, which is why this app uses `'simple'` — a search for `lammas` will *not* match `lammast` (different surface forms), only exact tokens match.
- **`tsquery`** is the query-side counterpart: `plainto_tsquery('simple', 'hunt lammast')` builds `'hunt' & 'lammast'`, and the `@@` operator checks whether a tsvector matches a tsquery.
- **GIN (Generalized Inverted Index)** is the index type that makes `@@` fast. "Inverted" is exactly the card-catalogue idea: instead of *document → words* (how the text is stored), it maps *word → documents*. Without it, `@@` still works but scans every row.
- **This app's pattern** (see `full-text-search-guide (1).md` and `TextSearchBuilder.java`): each searchable table gets *one* GIN index over the concatenation of its text columns —
  ```sql
  CREATE INDEX document__combined_search__fts_idx ON document.document USING GIN ((
      to_tsvector('simple', coalesce(title, '')) ||
      to_tsvector('simple', coalesce(content_text, ''))
  ));
  ```
  The query side (`TextSearchBuilder`) generates *exactly the same expression* — that's not cosmetic, it's required: Postgres only uses an expression index when the query's expression matches the index's expression. The builder picks `plainto_tsquery` for plain input, `phraseto_tsquery` for `"quoted phrases"`, and falls back to `LIKE` for `*wildcards*`.
- The cross-entity **quick search** (persons ∪ cars ∪ issuer firms ∪ insurances) runs each table's GIN search independently and `UNION ALL`s the matching person ids rather than joining all tables into one query — searching five tables costs roughly five index lookups instead of one row-multiplying mega-join.

---

## 2. Context search: embeddings and vector indexes

### In plain language

Imagine a huge map where every possible *sentence* has a location, and the map has a magical property: **sentences that mean similar things are placed close together**. "hunt sööb lammast" (a wolf eats a lamb), "kriimsilm sööb utte" (Old Scratch-Eye eats a ewe), and "võsavillem näksib voona" (the bush-wolf nibbles a lamb) all land in the same neighbourhood — the "predator eats livestock" district — even though they share almost no words. Meanwhile "auto vajab kindlustust" (a car needs insurance) is on the other side of the map entirely.

- An **embedding model** is the cartographer: give it a text, it returns that text's coordinates on the map.
- **Storing a document** = computing its coordinates once and saving them next to the document.
- **Context search** = computing the coordinates of *your query*, then asking "which stored documents are closest to this point?" — nearest neighbours on the meaning-map.

That's the whole idea. The reason this finds synonyms is that the model learned, from enormous amounts of text, that *kriimsilm* appears in the same kinds of sentences as *rebane/hunt* — so it places them nearby, without anyone ever writing a synonym dictionary.

Two things follow from the geometry:

1. **Results are ranked, not matched.** Every document has *some* distance to your query — there's no hard line between "found" and "not found," only "closer" and "farther." That's why the UI shows a similarity percentage and a top-N list, not an exact result set.
2. **Quality depends entirely on the cartographer.** This app uses `bge-m3`, a strong multilingual model that handles Estonian well — but how precisely it places archaic folk vocabulary like *võsavillem* is a property of the model, not of the database. A better model = a better map = better search, with no schema changes.

### The technical detail

- **Embedding**: a fixed-length array of floats — here 1024 numbers per text (`bge-m3`'s output size, hence the column `embedding vector(1024)`). Produced at upload time (for the document text) and at search time (for the query) by the Ollama service (`docker-compose` service `ollama`, HTTP API on `:11434`, called from `EmbeddingClient`).
- **pgvector** is the Postgres extension providing the `vector` type and distance operators. This app uses **cosine distance** (`<=>`): it compares the *direction* of two vectors, ignoring their length — the standard choice for text embeddings. Similarity shown in the API/UI is `1 - cosine_distance`, so ~1.0 = same meaning, ~0 = unrelated.
- **The query** is a nearest-neighbour search:
  ```sql
  SELECT ..., 1 - (embedding <=> $query_vector) AS similarity
  FROM document.document
  WHERE embedding IS NOT NULL
  ORDER BY embedding <=> $query_vector
  LIMIT 10;
  ```
- **HNSW index** (`USING hnsw (embedding vector_cosine_ops)`): the vector-world equivalent of the GIN index's job — making the search fast at scale. HNSW (Hierarchical Navigable Small World) builds a multi-layer "highway network" between vectors so a nearest-neighbour query hops toward the target instead of measuring the distance to every row. It's *approximate* (may very occasionally miss a true neighbour) in exchange for speed — irrelevant at hundreds of documents, essential at millions.
- **Degradation path**: if Ollama is down at upload time, the document is stored with a `NULL` embedding — still keyword-searchable, invisible to context search, and flagged `embedded: false` in the API response. If Ollama is down at *search* time, context search returns an empty list (there is no way to compare meanings without embedding the query).
- **Model changes require re-embedding.** Coordinates from different models live on *different maps* — you cannot compare a `bge-m3` vector to one from another model. Swapping models means re-computing every stored embedding (and changing the column dimension if the new model's output size differs).

### Why jOOQ doesn't know about the embedding column

A build-pipeline detail specific to this repo: jOOQ's code generator runs migrations against a throwaway *plain* Postgres (no pgvector), so all vector DDL is gated with Liquibase `context:!jooq` (see `18-document-vector.sql`) and skipped there. Real databases (dev/CI/prod, running the `pgvector/pgvector` image) get the column and index; generated jOOQ classes simply don't include the column, and `DocumentAdapter` touches it via raw SQL only. This keeps codegen fast and dependency-free at the cost of one column living outside the type-safe layer.

---

## 3. Where RAG fits (and why this feature isn't quite RAG)

**RAG = Retrieval-Augmented Generation.** It's a three-step pipeline for making an LLM answer questions using *your* documents instead of only its training data:

1. **Retrieve** — find the documents most relevant to the user's question. (Usually exactly the vector search described in §2, sometimes combined with keyword search — that combination is called *hybrid search*.)
2. **Augment** — paste the retrieved documents into the LLM's prompt as context: *"Using the following documents, answer the question…"*
3. **Generate** — the LLM writes an answer grounded in those documents, ideally citing them.

**What this app implements is step 1** — the retrieval engine, with the results shown directly to you for preview instead of being fed to an LLM. That's a complete, useful feature on its own (it's how "find me the document about X, however X was phrased" works), and it's also *the* foundational building block: turning it into full RAG later means adding a generation step on top — e.g. sending the top-5 retrieved documents plus the user's question to an LLM (Ollama can serve chat models too, so even that could stay local and free) and returning the generated answer alongside the sources.

In plain language: **semantic search hands you the relevant pages; RAG additionally reads them aloud and answers your question.** This app currently hands you the pages.

A full copy-paste-ready implementation walkthrough of both the A and G steps (plus a hybrid keyword+context endpoint) is in [`hybrid-search-and-rag-example.md`](hybrid-search-and-rag-example.md).

---

## 4. The wolf/lamb example, walked through both engines

Stored documents:

1. "kriimsilm sööb utte" *(folk name for fox + dialect word for ewe)*
2. "hunt sööb lammast" *(wolf eats a lamb — plain wording)*
3. "võsavillem näksib voona" *(folk name for wolf + nibbles + dialect word for lamb)*

Query: **"hunt sööb lammast"**

**Keyword engine**: tokenizes the query into `hunt`, `sööb`, `lammast`; consults the GIN index card for each; only document 2 contains them. Documents 1 and 3 share at most the word *sööb* (and #3 not even that) — from the card catalogue's point of view they're nearly unrelated to the query. Result: **document 2 only** (plus, for a single shared word like *sööb* with `plainto_tsquery`'s AND semantics, not even partial matches).

**Context engine**: embeds the query → a point in "predator eats young livestock" territory. All three documents were embedded at upload and live in that same neighbourhood, because the model learned these words appear in the same semantic contexts. Result: **all three documents, ranked** — document 2 first (near-identical meaning *and* wording, similarity approaching 1.0), documents 1 and 3 close behind with lower but clearly-above-noise similarity, and any unrelated documents (insurance memos, shopping lists) far down with low scores.

That ranking behaviour — not a hard "these 3 match, everything else doesn't" — is the honest shape of semantic search, and it's why the UI presents context results with similarity percentages while keyword results are a plain match list.
