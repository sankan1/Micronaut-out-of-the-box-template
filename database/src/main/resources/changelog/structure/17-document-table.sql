--liquibase formatted sql

--changeset sander:add-document-table
CREATE TABLE document.document (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    title VARCHAR(255) NOT NULL,
    content JSONB NOT NULL,
    content_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT document__id__pkey PRIMARY KEY (id)
);
--rollback DROP TABLE document.document;

--changeset sander:add-document-search-index
CREATE INDEX document__combined_search__fts_idx ON document.document USING GIN ((
    to_tsvector('simple', coalesce(title, '')) ||
    to_tsvector('simple', coalesce(content_text, ''))
));
--rollback DROP INDEX document.document__combined_search__fts_idx;
