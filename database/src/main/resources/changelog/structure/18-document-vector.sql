--liquibase formatted sql

-- Everything in this file is gated with context:!jooq: the jOOQ codegen pipeline
-- (:database:dump, run with --contexts=jooq) uses a plain Postgres image without the
-- pgvector extension, so these changesets only run against real databases
-- (dev docker-compose / CI / prod), never against the codegen throwaway DB.
-- Consequence: jOOQ never sees the embedding column - it is accessed via raw SQL only.

--changeset sander:add-pgvector-extension context:!jooq
CREATE EXTENSION IF NOT EXISTS vector;
--rollback DROP EXTENSION vector;

--changeset sander:add-document-embedding-column context:!jooq
ALTER TABLE document.document ADD COLUMN embedding vector(1024);
--rollback ALTER TABLE document.document DROP COLUMN embedding;

--changeset sander:add-document-embedding-index context:!jooq
CREATE INDEX document__embedding__hnsw_idx ON document.document
    USING hnsw (embedding vector_cosine_ops);
--rollback DROP INDEX document.document__embedding__hnsw_idx;
