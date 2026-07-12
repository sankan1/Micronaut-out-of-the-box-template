# Backend Technical Solution

Micronaut backend: OpenAPI-first contract, jOOQ persistence, Liquibase migrations, dual login (Keycloak OIDC + Estonian Smart-ID) with DB-backed sessions and role-group authorization, Unleash feature flags, full-text search over GIN indexes, **semantic (vector) document search via pgvector + a local Ollama embedding model**, a live WebSocket feed, Dockerized local infra, and a GitHub Actions CI pipeline.

Domain: `Person` owns `Car`s; each `Car` was sold by one `IssuerFirm` (reseller); `Insurance` ties a `Person` + `Car` together with an expiry date. `insurance.insurance_expiring_soon` is a live SQL view (not a maintained table) of insurances with fewer than 30 days left. Separately, `document.document` is a plain-text document store (JSONB content + flattened text + a 1024-dim embedding) searchable two ways: keyword (GIN) and context/semantic (pgvector cosine similarity) — concepts explained in depth in `search-explained.md`.

## 1. Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language / runtime | Java / Micronaut (Netty) | 21 / 4.6.13 |
| DB access | jOOQ | 3.21.4 |
| Migrations | Liquibase | 4.29.2 |
| Database | PostgreSQL (image `pgvector/pgvector:pg17` — official Postgres + the pgvector extension) | 17.x |
| Vector search | pgvector (`vector(1024)` column, HNSW index, cosine distance) | — |
| Embeddings | Ollama (docker-compose service) running `bge-m3` (multilingual, 1024-dim) | — |
| Feature flags | Unleash (`unleash-client-java`) | 12.2.2 (server: `unleashorg/unleash-server:7`) |
| OIDC provider | Keycloak | 25.0.0 |
| eID | `smart-id-java-client` | 3.2 |
| Logging | Logback (`logstash-logback-encoder` dependency present, console pattern in use) | — |
| Build | Gradle multi-module (root, `database`, `openapi`) | — |
| CI | GitHub Actions (`.github/workflows/backend-ci.yml`) | — |

## 2. Module Layout

- **root** (`com.example`) — application code; consumes generated sources.
- **`database`** — Liquibase changelogs; Gradle tasks `update` (apply to dev DB) and `dump` (produce a redacted SQL dump for jOOQ codegen). Also hosts `com.example.changelog.NumericFilenameComparator` (see §3).
- **`openapi`** — OpenAPI spec (`openapi/src/main/resources/swagger/openapi.yml`) and Micronaut OpenAPI server codegen config.

## 3. Code Generation Pipeline

- **OpenAPI** → `:openapi:generateServerOpenApiApis` / `generateServerOpenApiModels` generate `*Api` interfaces + request/response models into `src/generated/openapi/java`. Controllers implement the generated interfaces.
- **jOOQ** → `generateJooq` depends on `:database:dump`, which runs Liquibase against a throwaway Testcontainers Postgres to produce `src/generated/resources/liquibase/pg_dump.sql` (IPs/JDBC URLs regex-redacted). jOOQ then spins up its own Testcontainers Postgres from that dump and generates DAOs/POJOs/records for schemas `public`, `person`, `system`, `car`, `issuer_firm`, `insurance` into `src/generated/jooq/java`.
- **Liquibase** → `:database:update` applies `database/src/main/resources/changelog/db.changelog-master.yaml` (which `includeAll`s `structure/*.sql`, optionally `data/*.sql`) to the real dev database.
  - `includeAll`'s default sort is **lexical string order**, not numeric — `"10-..."` sorts before `"7-..."`. The `structure/` changelog uses a custom `resourceComparator: com.example.changelog.NumericFilenameComparator` (a small `Comparator<String>` in the `database` module, splits each path into digit/non-digit chunks and compares digit chunks numerically) so plain numeric filenames (`7-...`, `8-...`, ..., `18-...`) still run in the right order. This bit twice during development — once against the persistent dev DB, once against `:database:dump`'s throwaway DB (which starts from scratch every time, so it hits the bug unconditionally regardless of the dev DB's state) — before the comparator was added.
  - **pgvector DDL is context-gated out of codegen**: every changeset in `18-document-vector.sql` (`CREATE EXTENSION vector`, the `embedding vector(1024)` column, the HNSW index) carries `context:!jooq`. The dump task runs with `--contexts=jooq` → those changesets are skipped → the codegen Testcontainers DB stays a *plain* Postgres image and jOOQ never sees the `embedding` column (it's accessed via raw SQL only, in `DocumentAdapter`). Real databases run `update` with **no** context filter, where `!jooq` changesets execute normally. Consequence: dev/CI/prod Postgres must be a pgvector-capable image (`PG_IMAGE` in `.env`, `pgvector/pgvector:pg17` in CI), while codegen needs nothing special.
- `.env` is loaded by the `co.uzzu.dotenv.gradle` plugin and exported as real process environment variables to `./gradlew run`. It's committed (local-dev-only placeholder credentials, no real secrets) so CI can use the same values against a Postgres service container with zero overrides.

## 4. Database Schema

| Schema | Table / View | Purpose |
|---|---|---|
| `person` | `person(id, name, nickname, identity_code, age)` | `identity_code` and `age` are nullable (no `NOT NULL` — added via `ALTER TABLE` onto a table that may already have rows) |
| `car` | `car(id, owner_id NULL→person, mark, model, created_at)` | `owner_id` starts `NULL` — a car is created unowned; assigning an owner is a separate action (§6.5) |
| `issuer_firm` | `issuer_firm(id, car_id→car, firm_name)` | Always created together with its `car` in one request — see `CreateCar` use case |
| `insurance` | `insurance(id, person_id→person, car_id→car, insurer_name, plan, amount, expiry_date, created_at)` | `plan` is free text (`KASKO`, liability, etc.) — deliberately not a DB enum, since the full set wasn't known up front |
| `insurance` | `insurance_expiring_soon` (VIEW) | `SELECT ... (expiry_date - CURRENT_DATE) AS days_left ... WHERE (expiry_date - CURRENT_DATE) < 30`. A view, not a maintained table — Postgres can't use `CURRENT_DATE` in a generated/stored column, and a view stays correct with zero batch-job maintenance |
| `document` | `document(id, title, content JSONB, content_text TEXT, created_at, embedding vector(1024))` | Plain-text document store. `content` = `{"title", "paragraphs": [...]}` (source of truth, parseable back to text); `content_text` = flattened text (GIN target, embedding source, preview). `embedding` is nullable — a doc uploaded while Ollama is down is stored un-embedded (keyword-searchable, invisible to context search, flagged `embedded:false` in the API). GIN index over title+content_text; HNSW index (`vector_cosine_ops`) over embedding |
| `system` | `user(user_id, uuid, ssn, email, first_name, last_name, created_at)` | Application user, keyed by unique SSN/UUID |
| `system` | `user_role(user_role_id, user_id, role)` | Flat role strings per user, unique per `(user_id, role)` |
| `system` | `user_authentication(user_authentication_id, user_id, auth_method, session_id, access_token, refresh_token, token_expiration, session_expiration, last_activity, created_at)` | One active session per user (`UNIQUE(user_id)`); holds OIDC tokens or is null for Smart-ID |

GIN full-text indexes (per `full-text-search-guide (1).md`) exist on `person.person`, `car.car`, `issuer_firm.issuer_firm`, and `insurance.insurance` — each a single index over the `to_tsvector('simple', coalesce(...)) || ...` concatenation of that table's searchable text columns (changeset `a9-search-indexes.sql`, see §3 for why filenames don't start at `1`).

## 5. Domain Architecture

Every entity follows the same layering: `Controller` (implements a generated `*Api` interface) → one `UseCase` class per action → an `Adapter` (jOOQ DSL queries, one per entity) → jOOQ. `Mapper` classes convert jOOQ POJOs ⇄ OpenAPI DTOs; `business.search.*CriteriaMapper` classes build jOOQ `Condition`s from search-request DTOs.

- **Person**: `PersonController` → `CreatePerson` / `GetPerson` / `UpdatePerson` / `SearchPersons` / `GetPersons` (legacy list-all, still gated by the `get-persons` Unleash flag) → `PersonAdapter`. `SearchPersons` supports structured filters (`name`, `nickname`, `identityCode`, `carMark`, `carModel`, `issuerFirmName`, `insurancePlan` — the cross-entity ones resolve via subqueries joining `car`/`issuer_firm`/`insurance`) plus a `textSearch` field that hits the GIN index via `TextSearchBuilder`.
- **Car**: `CarController` → `CreateCar` (creates the `Car` **and** its `IssuerFirm` in one call — a car is never created without a reseller), `GetCar`, `UpdateCar` (mark/model only), `AssignCarOwner` (sets `owner_id` — the only way ownership changes), `DeleteCar`, `SearchCars` (filters include exact `ownerId`/`carId` matches alongside the fuzzy `mark`/`model`/`ownerName`/`issuerFirmName`/`unownedOnly`/`textSearch` ones — the exact-match filters back `PersonDetail`'s "this person's cars" lookup in the frontend).
- **IssuerFirm**: read-only — `IssuerFirmController` → `SearchIssuerFirms`. No create/update/delete endpoints; an issuer firm only ever comes into existence via `CreateCar`.
- **Insurance**: `InsuranceController` → `CreateInsurance`, `GetInsurance`, `UpdateInsurance`, `DeleteInsurance`, `SearchInsurances` (same exact-match-plus-fuzzy filter pattern as cars, via `personId`/`carId`), `GetExpiringSoonInsurances` (reads the view). Create/update also call `InsuranceWebSocket.broadcastUpdate()` (§9).
- **Quick search**: `QuickSearchController` → `QuickSearchPersons` → `QuickPersonSearchCriteriaMapper`. Implements the `full-text-search-guide (1).md` pattern exactly: search `person`, `car` (joined via `owner_id`), `issuer_firm` (joined via `car → owner_id`), and `insurance` (direct `person_id`) independently against each table's own GIN index, `UNION ALL` the matching person ids, then filter the main `Person` query by that id set — avoids a single multi-table `JOIN` that would multiply rows.
- **Search utilities**: `com.example.util.search.TextSearchBuilder` builds the actual jOOQ `Condition` for a text input — `plainto_tsquery` for plain words, `phraseto_tsquery` for `"quoted phrases"`, a `LIKE`/wildcard fallback for `*`-containing input. `com.example.business.global.SortMapper` parses the frontend's `"field:asc"` sort-param strings into jOOQ `OrderField`s against a per-entity allow-list.
- **Documents** (keyword + semantic search; see `search-explained.md` for the concepts): `DocumentController` (**`@ExecuteOn(TaskExecutors.BLOCKING)`** — it does blocking JDBC *and* a blocking HTTP call to Ollama, neither of which may run on a Netty event-loop thread; omitting this was a real bug caught in testing) → `CreateDocument` (splits pasted text into paragraphs on blank lines → JSONB, flattens to `content_text`, embeds via `EmbeddingClient`, stores — degrading gracefully to an un-embedded doc if Ollama is unreachable), `GetDocument`, `KeywordSearchDocuments` (reuses `TextSearchBuilder` over title+content_text), `ContextSearchDocuments` (embeds the *query*, then nearest-neighbour SQL: `ORDER BY embedding <=> $vec::vector LIMIT k`, similarity = `1 - cosine_distance`). `EmbeddingClient` (`business.embedding`) posts to Ollama's `/api/embed` via a service-id HTTP client (`micronaut.http.services.embedding`, 60s read timeout — Ollama cold-loads the model after idle). No role restrictions: any authenticated user may upload/search (user decision).
- **Auth/user slice** (ports & adapters): `auth.user.persistence.port` (`UserAuthenticationPort`, `UserRolePort`) implemented by jOOQ adapters in `auth.user.persistence.adapter`; orchestration in `auth.user.service` (`UserService`, `SmartIdLoginService`, `SessionValidationService`, `TokenRefreshService`, `LogoutService`, `UserRoleService`, `RoleGroupResolver`, `SessionPolicy`).
- **Authorization**: `com.example.auth.security.RequiresRoleGroup` + `RequiresRoleGroupInterceptor` — a Micronaut AOP `@Around` interceptor, see §6.6.

## 6. Authentication

### 6.1 Session model

Authentication mode is `cookie`, not stateless JWT. The `authToken` cookie carries an **internal session UUID**, not the IdP's token.

- `SessionCookieLoginHandler` replaces Micronaut's `TokenCookieLoginHandler` to write the internal session id into the cookie, and works around a Micronaut Security 4.10.1 bug where absolute `redirect.login-success` URLs get mangled by context-path concatenation.
- `SessionAuthenticationFetcher` (highest-precedence `AuthenticationFetcher`) reads the cookie and resolves it via `SessionValidationService`.
- Every validated request "touches" the session: `last_activity` is updated and `session_expiration` is pushed forward by `app.session.absolute-lifetime` (default `12h`) — a sliding absolute-lifetime window, enforced in `SessionPolicy`.
- For OIDC sessions, validation also checks token expiry and triggers `TokenRefreshService` transparently.
- When both `oidc-auth` and `smart-id-auth` Unleash flags are off, `SessionAuthenticationFetcher` returns a synthetic `"anonymous-bypass"` authentication with **zero roles** — useful for poking at read-only endpoints without logging in, but every `@RequiresRoleGroup`-protected mutation correctly 403s under it, since "zero roles" never matches any allowed group.

### 6.2 OIDC login (Keycloak)

Standard Micronaut Security OAuth2/OpenID `authorization_code` client with PKCE (cookie-persisted).

- `OidcAuthenticationMapper` (`@Named("oidc")`) maps IdP claims to an internal user: finds/creates `system.user`, reads roles from the ID token's `roles` claim (see §6.6 — this used to incorrectly read the OAuth `scope` string instead), opens a session row. **Note:** it currently assigns a synthetic, checksum-valid Estonian SSN to new users rather than reading a real `personal_code` claim (the `CLAIM_SSN` constant is defined but unused) — a demo-only placeholder.
- `TokenRefreshService` row-locks the session (`SELECT … FOR UPDATE`) and refreshes the access token within 30s of expiry; keeps the existing stored roles as-is on refresh (a token refresh response carries no fresh ID token claims here, so there's nothing newer to read); terminates the session on `invalid_grant`/`invalid_token`; on transient IdP/network failure it keeps a still-valid token and only denies once it has actually expired.
- Logout (`OidcEndSessionLogoutHandler` + `ModernKeycloakEndSessionEndpoint` + `KeycloakEndSessionEndpointFactory`) clears cookies and redirects to Keycloak's RP-Initiated Logout endpoint using `post_logout_redirect_uri`/`client_id`. Two Micronaut/Keycloak-version mismatches are worked around here: Micronaut's Keycloak auto-detection only recognizes the legacy pre-17 issuer path, and Keycloak 19+ dropped the old `redirect_uri` logout parameter.
- `CustomAuthorizationRedirectHandler` forces `ui_locales=et` on the authorization redirect.
- `LogoutService` revokes both access and refresh tokens at the provider's `/protocol/openid-connect/revoke` endpoint (best-effort, failures only logged).

### 6.3 Smart-ID login (Estonian eID)

Two-step poll flow against SK's public demo environment, implemented in `SmartIdAuthController`:

1. `POST /smart-id/init` — given an 11-digit identity code, opens a Smart-ID notification-authentication session, caches the pending session (`PendingSmartIdAuthentication`) under a random reference in a 5-minute Caffeine cache (`smart-id-sessions`), returns `reference` + `verificationCode` (the PIN shown in the user's Smart-ID app).
2. `POST /smart-id/complete?reference=` — polls the final session status, validates the signed response, logs the user in via `SmartIdLoginService` (creating the user if new, defaulting role to the literal string `end-user`, which **does** match `app.roles.end-user-roles` — Smart-ID logins get a real END_USER role group today, unlike OIDC before the §6.6 fix), and sets the `authToken` cookie directly.

`SmartIdClientFactory` wires the `SmartIdClient`/`CertificateValidator`/response validator and loads an optional JKS truststore, falling back to the JVM default trust manager if it's missing or unreadable.

**Known gap:** the bundled `smart-id-java-client` does not ship SK's trust-anchor root certificate, so certificate validation fails until it is sourced from SK's trust documentation and wired into `SmartIdClientFactory`.

### 6.4 Dual-method gating (`AuthFeatureFlags`)

| `oidc-auth` | `smart-id-auth` | Effect |
|---|---|---|
| OFF | OFF | Auth bypassed entirely for every request, including login (dev-only; never both off in production) |
| ON | OFF | Only OIDC accepted; Smart-ID endpoints return 403 |
| OFF | ON | Only Smart-ID accepted; OIDC mapper returns `AuthenticationResponse.failure` |
| ON | ON | Both accepted |

### 6.5 Role groups vs. raw roles

- Raw roles are plain strings, stored per-user in `system.user_role`.
- `RoleGroupResolver` buckets raw roles into `ADMIN` / `HEAD_USER` / `END_USER` via `app.roles.{admin-roles,head-user-roles,end-user-roles}` (`application.yml`), exposed as `roleGroups` on `GET /user`. The three groups are **not** hierarchical in code — `ADMIN` doesn't automatically satisfy a `HEAD_USER`-only check unless a raw role happens to appear in both configured lists. Every `@RequiresRoleGroup` usage in this codebase explicitly lists `ADMIN` alongside whichever group actually needs the permission (see the table in §6.6) — there's no implicit "admin can do everything" fallback at the authorization-check level, that's a property of which groups get listed on each annotation.
- URL interception (`micronaut.security.intercept-url-map`): `/oauth/**`, `/smart-id/**`, `/logout` → anonymous; `/**` → `isAuthenticated()`. This only gates *authentication* (logged in or not) — it has no concept of role groups.

### 6.6 Authorization (`@RequiresRoleGroup`)

Per-endpoint role-group authorization didn't exist at all until the Car/Insurance/IssuerFirm domain needed it (the original `Person` slice had no write endpoints to protect). It's a small custom AOP interceptor, not a Micronaut built-in:

- `com.example.auth.security.RequiresRoleGroup` — `@Retention(RUNTIME) @InterceptorBinding(kind=AROUND) @Type(RequiresRoleGroupInterceptor.class)`, takes `UserInfoOutputModalRoleGroupsInner[] value()` (reusing the generated enum, no duplicate role-group type).
- `RequiresRoleGroupInterceptor` resolves the caller's groups via `RoleGroupResolver.resolve(authentication.getRoles())` and throws `io.micronaut.security.authentication.AuthorizationException` (the same exception `@Secured` uses internally — already mapped to 403, no custom exception handler needed) if none of the caller's groups appear in the annotation's allowed list.

Current matrix (everything not listed is read-only and only requires `isAuthenticated()`):

| Action | Endpoint | Allowed groups |
|---|---|---|
| Create person | `POST /persons` | END_USER, ADMIN |
| Update person | `PUT /persons/{id}` | ADMIN |
| Create car (+ issuer firm) | `POST /cars` | END_USER, ADMIN |
| Update car | `PUT /cars/{id}` | ADMIN |
| Assign car owner | `PUT /cars/{id}/owner` | HEAD_USER, ADMIN |
| Delete car | `DELETE /cars/{id}` | ADMIN |
| Create insurance | `POST /insurances` | HEAD_USER, ADMIN |
| Update insurance | `PUT /insurances/{id}` | ADMIN |
| Delete insurance | `DELETE /insurances/{id}` | ADMIN |

**Getting real role groups end-to-end required two separate fixes**, both now in place:

1. **`OidcAuthenticationMapper` was reading the wrong source.** It built roles from `tokenResponse.getScope()` — but the OAuth scope a client requests (here, the static string `"openid profile email"`) is not the same thing as a user's application roles, and split-by-space treated `"openid"`/`"profile"`/`"email"` themselves as roles. The fix reads the actual ID token `roles` claim instead (`openIdClaims.getClaims().get("roles")`, see `extractRoles()`), which is the claim a custom Keycloak protocol mapper (`oidc-usermodel-client-role-mapper`) is configured to populate from the user's *client* roles.
2. **The Keycloak protocol mapper itself was incomplete.** `oidc-usermodel-client-role-mapper` needs a `usermodel.clientId` config value telling it which client's roles to read — `keycloak/micronaut-realm.json`'s `roles` mapper was missing it, so the claim would have come back empty even after fix #1. Added `"usermodel.clientId": "micronaut-app"` to the mapper config.

To actually exercise all three role tiers, the `micronaut-app` Keycloak client now has three client roles (`example.admin`, `example.head-user`, `example.end-user` — matching `app.roles.*` in `application.yml`) assigned one-per-user to three test accounts, all password `password123`:

| User | Client role | Resolves to |
|---|---|---|
| `adminuser` | `example.admin` (+ legacy `admin`/`user`) | ADMIN |
| `headuser` | `example.head-user` (+ `user`) | HEAD_USER |
| `testuser` | `example.end-user` (+ legacy `user`) | END_USER |

`keycloak/micronaut-realm.json` reflects all of this, so it survives a fresh realm import (`--import-realm` skips re-importing into an *already-running* Keycloak with that realm name — if you're updating an existing local Keycloak rather than starting fresh, the role/user/mapper changes need to be applied via the Admin API or console, not just by editing the JSON).

### 6.7 Swapping in a real Keycloak instance

The realm (§6.6) is real Keycloak running a real OIDC flow — nothing about it is mocked. What's demo-specific is purely the *content* of the `micronaut` realm: throwaway users, a committed client secret, and the synthetic-SSN placeholder in `OidcAuthenticationMapper` (§6.2). Going from this local realm to a production one is a configuration change, not a code change, provided the following gets carried over correctly:

**Changes (don't reuse the local values):**
- Client secret — generate a fresh one on the new realm; never reuse `micronaut-secret`.
- Redirect URIs / web origins on the client — the real frontend/backend origins, not `localhost:9000`/`localhost:8080`.
- Users — real accounts instead of `testuser`/`adminuser`/`headuser`.
- Four env vars on the backend: `OIDC_ISSUER_URL`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `APP_DOMAIN`.

**Must be replicated, not just swapped** — easy to miss because nothing fails loudly if you don't; auth just silently grants zero role groups instead of erroring:
- The `roles` protocol mapper (`oidc-usermodel-client-role-mapper`), with `usermodel.clientId` set to whatever the new client is named — this is the exact piece that was missing locally until §6.6's fix #2.
- The client roles matching `app.roles.{admin-roles,head-user-roles,end-user-roles}` in `application.yml` (`example.admin`/`example.head-user`/`example.end-user`, or whatever names you choose as long as the config matches), assigned to the real users.

**Stays identical:**
- Every line of application code — the OIDC integration only ever reads the four env vars above plus standard OIDC discovery; nothing is hardcoded to the `micronaut` realm's name or contents.
- The realm *name* doesn't need to change either; it's an arbitrary label, not a meaningful identifier to the app or to Keycloak itself. A single Keycloak server can also host the dev and prod realms side by side, isolated from each other, if that's preferable to running two separate Keycloak deployments.

One real (not just configuration) difference worth flagging: `keycloak/micronaut-realm.json` is itself a valid Keycloak realm-export — the Admin Console's "Create realm" page accepts a JSON upload as a starting point, so importing this file into a fresh production Keycloak and then editing the secret/URIs/users via the UI is a faster path than recreating the client/roles/mapper by hand.

### 6.8 Login-redirect note (frontend, cross-referenced here because it gates every authenticated backend call)

Not a backend bug, but worth knowing when debugging "the backend correctly returns 401 but nothing redirects to login": the Vue frontend's router-guard reads the `oidc-auth`/`smart-id-auth` Unleash flags *synchronously* to decide whether to force a login redirect, while the Unleash client's first flag fetch is still async and in flight on a cold page load (`app.use(router)` kicks off Vue Router's first navigation immediately, before the flag fetch resolves). The frontend fix awaits the Unleash client's initial fetch before even constructing the Vue app. Mentioned here because it's easy to misattribute the symptom ("login redirect doesn't happen") to the backend's auth config when the backend was working correctly the whole time.

## 7. Feature Flags (Unleash)

| Flag | ON | OFF |
|---|---|---|
| `get-persons` | `GET /persons` returns data | Returns an empty list |
| `persons-table` | Frontend renders the Persons Tabulator table | Frontend renders nothing for that component — **this flag must exist and be enabled** in any new Unleash instance; nothing creates it automatically, and the Persons list silently shows zero rows forever if it's missing (this happened once during development) |
| `oidc-auth` | OIDC login enforced | Rejected only if `smart-id-auth` is ON; bypassed if both OFF |
| `smart-id-auth` | Smart-ID login enforced | Rejected only if `oidc-auth` is ON; bypassed if both OFF |

Client wired in `UnleashFactory` as a `DefaultUnleash` bean, scoped to the `development` environment via the client API token. Flags are created via the Unleash Admin API/UI — they're not declared anywhere in code, so a fresh Unleash instance starts with *none* of them existing, which defaults every `isEnabled()` call to `false`. Worth checking first if a flag-gated feature "does nothing."

## 8. API Surface

| Method | Path | Controller | Notes |
|---|---|---|---|
| GET | `/user` | `UserController` | |
| GET | `/persons` | `PersonController` | Legacy list-all, flag-gated by `get-persons` |
| GET | `/persons/{id}` | `PersonController` | |
| PUT | `/persons/{id}` | `PersonController` | ADMIN |
| POST | `/persons` | `PersonController` | END_USER, ADMIN |
| POST | `/persons/search` | `PersonController` | Detailed search (structured filters + `textSearch`) |
| GET | `/persons/quick/search` | `QuickSearchController` | Cross-table GIN search, `UNION ALL` pattern |
| POST | `/cars` | `CarController` | END_USER, ADMIN — creates the `Car` + its `IssuerFirm` together |
| GET | `/cars/{id}` | `CarController` | |
| PUT | `/cars/{id}` | `CarController` | ADMIN |
| PUT | `/cars/{id}/owner` | `CarController` | HEAD_USER, ADMIN |
| DELETE | `/cars/{id}` | `CarController` | ADMIN |
| POST | `/cars/search` | `CarController` | |
| POST | `/issuer-firms/search` | `IssuerFirmController` | Read-only — no create/update/delete |
| POST | `/insurances` | `InsuranceController` | HEAD_USER, ADMIN; also broadcasts on `/ws/insurance` |
| GET | `/insurances/{id}` | `InsuranceController` | |
| PUT | `/insurances/{id}` | `InsuranceController` | ADMIN; also broadcasts |
| DELETE | `/insurances/{id}` | `InsuranceController` | ADMIN; also broadcasts |
| POST | `/insurances/search` | `InsuranceController` | |
| GET | `/insurances/expiring-soon` | `InsuranceController` | Reads `insurance.insurance_expiring_soon` |
| POST | `/documents` | `DocumentController` | Any authenticated user; stores + embeds; `embedded:false` when Ollama was down |
| GET | `/documents/{id}` | `DocumentController` | Full text for preview |
| GET | `/documents/search` | `DocumentController` | Keyword (GIN) search, `?keyword=` |
| GET | `/documents/context-search` | `DocumentController` | Semantic search, `?query=&limit=`; results ranked with `similarity` |
| POST | `/smart-id/init` | `SmartIdAuthController` | |
| POST | `/smart-id/complete` | `SmartIdAuthController` | |
| GET | `/oauth/login/oidc` | framework (Micronaut Security OAuth2) | |
| GET | `/oauth/callback/oidc` | framework | |
| GET | `/logout` | framework + `OidcEndSessionLogoutHandler` | |
| GET | `/api/debug/oidc-config` | `DebugController` | Diagnostics: resolved OIDC client id/issuer/domain |

Per `README.md`, the app is served at `http://localhost:8080/api`; no `context-path` is currently set in `application.yml`, so this prefix is not actually applied except where a controller hardcodes it (`DebugController`) — every real route above is at the bare path (e.g. `/persons`, not `/api/persons`).

## 9. WebSocket

- **`/ws/persons`** (`PersonsWebSocket`): client `ping` → server `pong` keepalive. `broadcastUpdate()` sends an `UPDATE` event to all connected sessions but is not currently invoked anywhere — still a scaffold.
- **`/ws/insurance`** (`InsuranceWebSocket`): replies to **every** incoming message — including the client's periodic heartbeat ping, there's no special-casing of the message content — with the current `insurance_expiring_soon` snapshot as a JSON array, instead of a bare `"pong"`. `broadcastUpdate()` pushes the same snapshot to all connected sessions and *is* wired up: `InsuranceController` calls it after every create/update/delete. The frontend can't use vueuse's `heartbeat` option for this socket (it pattern-matches a literal pong string), so it drives the keep-alive ping manually and treats every inbound frame as a data payload — see `useInsuranceWebSocket.ts` in the Vue repo.

## 10. Configuration Reference

Key variables (see `.env`, defaults in `application.yml`):

| Variable | Purpose | Default |
|---|---|---|
| `PG_HOST` / `PG_PORT` / `PG_DATABASE` / `PG_USERNAME` / `PG_PASSWORD` | App datasource | `localhost:5432/thedb` |
| `APP_DOMAIN` | Frontend origin; used for CORS, login-success/logout redirects | `http://localhost:9000` |
| `OIDC_ENABLED` | Enables the `oauth2` client config | `false` |
| `OIDC_ISSUER_URL` / `OIDC_CLIENT_ID` / `OIDC_CLIENT_SECRET` | Keycloak realm/client | local Keycloak realm `micronaut` |
| `COOKIE_SECURE` | `Secure` flag on auth cookies | `false` |
| `SESSION_COOKIE_MAX_AGE` | Cookie lifetime | `1d` |
| `SESSION_ABSOLUTE_LIFETIME` | Server-side sliding session ceiling | `12h` |
| `UNLEASH_API_URL` / `UNLEASH_API_TOKEN` / `UNLEASH_APP_NAME` | Unleash client | local Unleash, `development` env token |
| `PG_IMAGE` | Postgres docker image for `local-db` (must include pgvector) | `pgvector/pgvector:pg17` |
| `EMBEDDING_URL` / `EMBEDDING_MODEL` | Ollama endpoint + embedding model (dims must match the `vector(1024)` column — changing model means re-embedding everything) | `http://localhost:11434` / `bge-m3` |
| `SMART_ID_ENABLED` / `SMART_ID_RP_UUID` / `SMART_ID_RP_NAME` / `SMART_ID_HOST_URL` | Smart-ID relying-party config | SK public demo (`00000000-0000-4000-8000-000000000000`) |
| `SMART_ID_TRUSTSTORE` / `SMART_ID_TRUSTSTORE_PASSWORD` | Smart-ID JKS truststore | bundled demo cert store |

`.env` is committed to the repo (placeholder local-dev credentials only, no real secrets) — both `./gradlew run` locally and the CI workflow (§12) rely on this so neither needs the values duplicated elsewhere.

## 11. Local Infrastructure (`docker-compose.yaml`)

| Service | Image | Notes |
|---|---|---|
| `local-db` | `${PG_IMAGE}` (`pgvector/pgvector:pg17`) | App database — pgvector-capable image required by `18-document-vector.sql` |
| `ollama` | `ollama/ollama` | Embedding model server on `:11434`. **One-time model download after first start:** `docker exec -it <project>-ollama-1 ollama pull bge-m3` (~1.2GB). Model unloads after idle; the first embed afterwards takes seconds (cold load), hence the 60s client timeout |
| `unleash-db` + `unleash` | `postgres` + `unleashorg/unleash-server:7` | Feature-flag service; admin UI on `:4242`. Flags aren't seeded — create `get-persons`, `persons-table`, `oidc-auth`, `smart-id-auth` manually on first setup (§7) |
| `keycloak-db` + `keycloak` | `postgres` + `quay.io/keycloak/keycloak` | OIDC provider; `--import-realm` auto-loads `keycloak/micronaut-realm.json` (realm `micronaut`, client `micronaut-app`, test users `testuser`/`adminuser`/`headuser`, all pw `password123`) on every startup, **skipping realms that already exist** — see the caveat in §6.6 if you're updating an existing realm rather than starting fresh |

## 12. CI (GitHub Actions)

`.github/workflows/backend-ci.yml` runs on every push to `main` and every pull request:

1. Validate the Gradle wrapper jar's checksum (`gradle/actions/wrapper-validation`).
2. Spin up a `postgres:17.5-bookworm` service container with the same `thedb`/`postgres`/`postgres` credentials as `.env`, so `:database:update` needs zero overrides.
3. `./gradlew :database:update` — applies every Liquibase changeset to that real Postgres.
4. `./gradlew generateJooq` — regenerates jOOQ sources via `:database:dump`'s own throwaway Testcontainers Postgres (Docker is preinstalled on GitHub-hosted runners). Steps 3 and 4 together are the most direct regression check available for migration-ordering bugs (§3) — one exercises a persistent DB, the other a from-scratch one.
5. `./gradlew build` — compiles, packages, runs `test` (currently a no-op, see §13).

It intentionally stops at build+test: no Docker image build/push, no deployment step. That's a deliberate scope decision, not an oversight — see `kubernetes-backend-implementation.md` §4 for how an image *would* be built if/when a CD pipeline is added on top of this.

## 13. Known Gaps

- Smart-ID: missing SK trust-anchor root certificate (see §6.3).
- OIDC: real `personal_code` claim is not consumed; a synthetic SSN is generated instead.
- **Test harness isn't wired up at all**: `src/test/groovy/com/example/DemoSpec.groovy` exists but no Groovy plugin/Spock dependency is configured in `build.gradle`, so `./gradlew test` reports `NO-SOURCE` — the file is never compiled or run. CI's `build` step's `test` task is consequently a no-op today, not a real safety net.
- WebSocket broadcast on `/ws/persons` (`PersonsWebSocket.broadcastUpdate`) is still not wired to any mutation (unlike `/ws/insurance`, which is).
- `logstash-logback-encoder` is a declared dependency but `logback.xml` uses a plain console pattern, not JSON output.
- One active session per user (`user_authentication.UNIQUE(user_id)`): a new login silently invalidates the user's previous session.
- Documented `/api` base path is not enforced by any `context-path` setting.
- `TokenRefreshService` keeps a session's existing roles as-is across a token refresh rather than re-reading a `roles` claim (refresh responses don't carry a fresh ID token in this flow) — fine in practice since role assignments rarely change mid-session, but worth knowing if a Keycloak role gets revoked while a session is still active: it won't take effect until the next full login.
