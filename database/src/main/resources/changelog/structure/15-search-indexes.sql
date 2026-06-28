--liquibase formatted sql

--changeset sander:add-person-search-index
CREATE INDEX person__combined_search__fts_idx ON person.person USING GIN ((
    to_tsvector('simple', coalesce(name, '')) ||
    to_tsvector('simple', coalesce(nickname, '')) ||
    to_tsvector('simple', coalesce(identity_code, ''))
));
--rollback DROP INDEX person__combined_search__fts_idx;

--changeset sander:add-car-search-index
CREATE INDEX car__combined_search__fts_idx ON car.car USING GIN ((
    to_tsvector('simple', coalesce(mark, '')) ||
    to_tsvector('simple', coalesce(model, ''))
));
--rollback DROP INDEX car__combined_search__fts_idx;

--changeset sander:add-issuer-firm-search-index
CREATE INDEX issuer_firm__combined_search__fts_idx ON issuer_firm.issuer_firm USING GIN (
    to_tsvector('simple', coalesce(firm_name, ''))
);
--rollback DROP INDEX issuer_firm__combined_search__fts_idx;

--changeset sander:add-insurance-search-index
CREATE INDEX insurance__combined_search__fts_idx ON insurance.insurance USING GIN ((
    to_tsvector('simple', coalesce(insurer_name, '')) ||
    to_tsvector('simple', coalesce(plan, ''))
));
--rollback DROP INDEX insurance__combined_search__fts_idx;
