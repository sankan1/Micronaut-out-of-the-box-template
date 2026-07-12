# Full Backend Guide

Every feature in this project as a step-by-step implementation guide. Each chapter is self-contained: the files involved, the code, a one-line reason where a reason is needed, and a verification step. Concepts are deliberately terse — the companion docs go deeper where it matters ([`search-explained.md`](search-explained.md), [`hybrid-search-and-rag-example.md`](hybrid-search-and-rag-example.md), [`kubernetes-backend-implementation.md`](kubernetes-backend-implementation.md)).

## Table of contents

1. [Project map & local infrastructure](#1-project-map--local-infrastructure)
2. [Database migrations (Liquibase)](#2-database-migrations-liquibase)
3. [jOOQ code generation](#3-jooq-code-generation)
4. [OpenAPI spec → server code generation](#4-openapi-spec--server-code-generation)
5. [The entity slice pattern](#5-the-entity-slice-pattern)
6. [Authentication](#6-authentication)
   - [6.1 DB-backed cookie sessions](#61-db-backed-cookie-sessions)
   - [6.2 OIDC login (Keycloak)](#62-oidc-login-keycloak)
   - [6.3 Smart-ID login](#63-smart-id-login)
   - [6.4 Feature-flag gating of auth methods](#64-feature-flag-gating-of-auth-methods)
7. [Authorization: role groups](#7-authorization-role-groups)
8. [Feature flags (Unleash)](#8-feature-flags-unleash)
9. [Full-text search (GIN indexes)](#9-full-text-search-gin-indexes)
   - [9.1 The indexes](#91-the-indexes)
   - [9.2 TextSearchBuilder & SortMapper](#92-textsearchbuilder--sortmapper)
   - [9.3 Detailed search](#93-detailed-search)
   - [9.4 Quick search (cross-entity UNION ALL)](#94-quick-search-cross-entity-union-all)
10. [Semantic search (pgvector + embeddings)](#10-semantic-search-pgvector--embeddings)
    - [10.1 Infrastructure: pgvector & Ollama](#101-infrastructure-pgvector--ollama)
    - [10.2 The document store & row vectorization](#102-the-document-store--row-vectorization)
    - [10.3 The context-search endpoint](#103-the-context-search-endpoint)
11. [WebSockets](#11-websockets)
12. [CI: GitHub Actions](#12-ci-github-actions)
13. [Further reading](#13-further-reading)

---

## 1. Project map & local infrastructure

Three Gradle modules; generated code is committed under `src/generated`.

```
root (com.example)          application code; consumes generated sources
├── database/               Liquibase changelogs + tasks :database:update / :database:dump
├── openapi/                OpenAPI spec + Micronaut server codegen config
├── src/generated/jooq/     jOOQ DAOs/POJOs/records   (output of generateJooq)
├── src/generated/openapi/  *Api interfaces + models  (output of openapi codegen)
└── src/main/java/com/example/
    ├── auth/               sessions, OIDC, Smart-ID, role security
    ├── business/           adapter / useCase / mapper / search / embedding / global
    ├── controller/         REST controllers + WebSockets
    └── config/             bean factories (DaoFactory, UnleashFactory)
```

Local infra is docker-compose (`docker compose up -d`): `local-db` (Postgres with pgvector), `unleash` + `unleash-db`, `keycloak` + `keycloak-db`, `ollama`. All credentials/ports come from the committed `.env`, loaded into Gradle by the `co.uzzu.dotenv.gradle` plugin:

```groovy
// build.gradle - .env values become real env vars for ./gradlew run
tasks.withType(JavaExec).configureEach {
    environment(env.allVariables())
}
```

**Verify:** `docker compose up -d && ./gradlew run` → `Startup completed ... Server Running: http://localhost:8080`.

---

## 2. Database migrations (Liquibase)

**Files:** `database/src/main/resources/changelog/db.changelog-master.yaml`, `changelog/structure/N-*.sql`, `database/src/main/java/com/example/changelog/NumericFilenameComparator.java`

One formatted-SQL file per logical change, numbered `1-...` through `18-...`, each changeset with a rollback:

```sql
--liquibase formatted sql

--changeset sander:add-car-table
CREATE TABLE car.car (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    owner_id BIGINT,
    mark VARCHAR(255) NOT NULL,
    model VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT car__id__pkey PRIMARY KEY (id),
    CONSTRAINT car__owner_id__fkey FOREIGN KEY (owner_id) REFERENCES person.person (id)
);
--rollback DROP TABLE car.car;
```

The master changelog uses `includeAll` — but its default sort is **lexical**, so `10-` would run before `7-`. A custom comparator fixes ordering (this bit twice before it existed):

```yaml
databaseChangeLog:
  - includeAll:
      path: structure
      relativeToChangelogFile: true
      resourceComparator: com.example.changelog.NumericFilenameComparator
  - includeAll:
      path: data
      relativeToChangelogFile: true
      errorIfMissingOrEmpty: false
```

```java
public class NumericFilenameComparator implements Comparator<String> {
    private static final Pattern CHUNK = Pattern.compile("\\d+|\\D+");

    @Override
    public int compare(String left, String right) {
        Matcher l = CHUNK.matcher(left), r = CHUNK.matcher(right);
        while (l.find() && r.find()) {
            int result = isDigits(l.group()) && isDigits(r.group())
                ? Long.compare(Long.parseLong(l.group()), Long.parseLong(r.group()))
                : l.group().compareTo(r.group());
            if (result != 0) return result;
        }
        return left.length() - right.length();
    }
}
```

Changesets can be **context-gated**: `--changeset sander:x context:!jooq` runs on normal `update` but is skipped when Liquibase runs with `--contexts=jooq` — the codegen pipeline exploits this (§3, §10.1).

**Verify:** `./gradlew :database:update` → `Liquibase: Update has been successful`.

---

## 3. jOOQ code generation

**Files:** `build.gradle` (jooq block), `database/build.gradle` (`dump` task)

Pipeline: migrations → SQL dump → throwaway Postgres → generated Java. No live DB needed.

```groovy
// database/build.gradle - replay all changesets against a Testcontainers PG, emit SQL
tasks.register('dump', JavaExec) {
    mainClass = 'liquibase.integration.commandline.LiquibaseCommandLine'
    args += '--driver=org.testcontainers.jdbc.ContainerDatabaseDriver'
    args += "--url=jdbc:tc:postgresql:${pgVersion}:///"
    args += "--changeLogFile=changelog/db.changelog-master.yaml"
    args += "--output-file=../${generatedSources}/${pgDump}"
    args += 'update-sql'
    args += '--contexts=jooq'      // skips context:!jooq changesets (pgvector DDL)
    doLast {                       // redact IPs/JDBC URLs from the committed dump
        def f = file("../${generatedSources}/${pgDump}")
        f.text = f.text.replaceAll(/(?m)^.*(192\.168\.|10\.\d|172\.(1[6-9]|2\d|3[01])\.|jdbc:).*$/, '-- [redacted]')
    }
}
```

```groovy
// build.gradle - jOOQ boots its own Testcontainers PG from that dump
jooq {
    configurations {
        main {
            generationTool {
                jdbc {
                    driver = 'org.testcontainers.jdbc.ContainerDatabaseDriver'
                    url = "jdbc:tc:postgresql:${pgVersion}:///jooq-db?TC_INITSCRIPT=file:${project.rootDir}/${generatedSources}/${pgDump}"
                }
                generator {
                    database {
                        name = 'org.jooq.meta.postgres.PostgresDatabase'
                        excludes = 'databasechangelog|databasechangeloglock'
                        schemata {
                            schema { inputSchema = 'public' }
                            schema { inputSchema = 'person' }
                            schema { inputSchema = 'system' }
                            schema { inputSchema = 'car' }
                            schema { inputSchema = 'issuer_firm' }
                            schema { inputSchema = 'insurance' }
                            schema { inputSchema = 'document' }
                        }
                    }
                    generate { daos = true; pojos = true; fluentSetters = true }
                    target {
                        directory = "${generatedSources}/jooq/java"
                        packageName = "${group}.jooq"
                    }
                }
            }
        }
    }
}

generateJooq { dependsOn ':database:dump', 'cleanGenerateJooq' }
```

New schema? Add a `schema { inputSchema = '...' }` line, run `./gradlew generateJooq`, commit the output. Table classes end in `_` where the table name equals its schema name (`Person.PERSON_`, `Car.CAR_`).

**Verify:** `./gradlew generateJooq` → `BUILD SUCCESSFUL`, classes under `src/generated/jooq/java/com/example/jooq/<schema>/`.

---

## 4. OpenAPI spec → server code generation

**Files:** `openapi/src/main/resources/swagger/openapi.yml`, `openapi/build.gradle`

Spec-first: endpoints and models are declared in YAML; controllers *implement* generated interfaces, so the API contract can't drift from the code.

```yaml
# openapi.yml - a path + schema, in this project's compact style
  /cars/{id}/owner:
    put:
      tags: [cars]
      operationId: assignCarOwner
      parameters:
        - { name: id, in: path, required: true, schema: { type: integer } }
      requestBody:
        content:
          application/json:
            schema: { $ref: '#/components/schemas/CarOwnerAssignRequest' }
      responses:
        '200':
          description: Car with owner assigned
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Car' }
```

```groovy
// openapi/build.gradle - Micronaut server codegen, output copied to src/generated
micronaut {
    openapi {
        server(file('src/main/resources/swagger/openapi.yml')) {
            alwaysUseGenerateHttpResponse = true
            apiPackageName = "${group}.openapi.api"
            modelPackageName = "${group}.openapi.model"
            useReactive = false
        }
    }
}
```

Workflow for any API change: edit `openapi.yml` → `./gradlew :openapi:generateServerOpenApiModels :openapi:generateServerOpenApiApis` → implement the new interface method in a controller (the compiler tells you). The same YAML is copied to the Vue repo (`openapi/openapi.yml`) where Orval generates the typed client (`npm run generate:openapi`).

**Verify:** regenerate, then `./gradlew compileJava` — a missing controller method fails compilation.

---

## 5. The entity slice pattern

**Files (Car as the example):** `business/adapter/CarAdapter.java`, `business/useCase/*.java`, `business/mapper/CarMapper.java`, `controller/CarController.java`, `config/DaoFactory.java`

Every entity follows: `Controller` (implements generated API) → one `UseCase` class per action → `Adapter` (all jOOQ) → DB. `Mapper` converts jOOQ POJO ⇄ OpenAPI model.

```java
// config/DaoFactory.java - jOOQ DAOs as beans
@Factory
public class DaoFactory {
    @Singleton
    public CarDao carDao(DSLContext dslContext) {
        return new CarDao(dslContext.configuration());
    }
    // ... one per entity
}
```

```java
// adapter: jOOQ DSL + DAO; denormalized rows for list views via join
@Singleton
public class CarAdapter {
    private final DSLContext dsl;
    private final CarDao dao;

    public Car getById(Long id) { return dao.fetchOneById(id); }

    public List<CarRow> search(Condition condition, List<OrderField<?>> orderFields, int page, int size) {
        return baseSelect().where(condition).orderBy(orderFields)
            .limit(size).offset(page * size).fetch(CarAdapter::toRow);
    }

    private SelectOnConditionStep<Record6<Long, Long, String, String, String, String>> baseSelect() {
        return dsl.select(CAR_.ID, CAR_.OWNER_ID, PERSON_.NAME, CAR_.MARK, CAR_.MODEL, ISSUER_FIRM_.FIRM_NAME)
            .from(CAR_)
            .leftJoin(PERSON_).on(PERSON_.ID.eq(CAR_.OWNER_ID))
            .leftJoin(ISSUER_FIRM_).on(ISSUER_FIRM_.CAR_ID.eq(CAR_.ID));
    }

    public record CarRow(Long id, Long ownerId, String ownerName, String mark, String model, String issuerFirmName) {}
}
```

```java
// use case: one action, one class; @Transactional is MANDATORY on anything touching jOOQ -
// without it every request dies with "No current connection present" (a real bug here once)
@Singleton
public class CreateCar {
    private final CarAdapter carAdapter;
    private final IssuerFirmAdapter issuerFirmAdapter;

    @Transactional
    public CarAdapter.CarRow execute(CarCreateRequest request) {
        Car car = carAdapter.create(CarMapper.mapToNewCar(request));
        issuerFirmAdapter.create(new IssuerFirm().setCarId(car.getId()).setFirmName(request.getIssuerFirmName()));
        return carAdapter.getRowById(car.getId());
    }
}
```

```java
// controller: thin - delegates, maps, sets status; role checks are annotations (§7)
@Controller
public class CarController implements CarsApi {
    @Override
    @RequiresRoleGroup({UserInfoOutputModalRoleGroupsInner.HEAD_USER, UserInfoOutputModalRoleGroupsInner.ADMIN})
    public HttpResponse<@Valid Car> assignCarOwner(Integer id, CarOwnerAssignRequest req) {
        var row = assignCarOwner.execute(id.longValue(), req.getPersonId().longValue());
        return row == null ? HttpResponse.notFound() : HttpResponse.ok(CarMapper.mapToCar(row));
    }
}
```

Checklist for a new entity: migration (§2) → `generateJooq` (§3) → spec + regen (§4) → DaoFactory bean → adapter → use cases (`@Transactional`!) → mapper → controller → role annotations (§7).

**Verify:** `curl -X POST -H "Content-Type: application/json" -d '{"mark":"Toyota","model":"Corolla","issuerFirmName":"AutoHouse"}' localhost:8080/cars`.

---

## 6. Authentication

### 6.1 DB-backed cookie sessions

**Files:** `auth/filter/SessionAuthenticationFetcher.java`, `auth/oidc/SessionCookieLoginHandler.java`, `auth/user/service/SessionValidationService.java`, `SessionPolicy.java`, `auth/oidc/TokenRefreshService.java`

Model: `micronaut.security.authentication: cookie`, but the `authToken` cookie carries an **internal session UUID**, never an IdP token. Sessions live in `system.user_authentication` (one per user, `UNIQUE(user_id)` — new login evicts the old session).

```yaml
# application.yml - URL-level auth: everything requires login except the auth endpoints
micronaut:
  security:
    authentication: cookie
    intercept-url-map:
      - { pattern: /oauth/**,    access: [ isAnonymous() ] }
      - { pattern: /smart-id/**, access: [ isAnonymous() ] }
      - { pattern: /logout,      access: [ isAnonymous() ] }
      - { pattern: /**,          access: [ isAuthenticated() ] }
```

Every request goes through the highest-precedence `AuthenticationFetcher`:

```java
@Singleton
public class SessionAuthenticationFetcher implements AuthenticationFetcher<HttpRequest<?>> {
    @Override
    public Publisher<Authentication> fetchAuthentication(HttpRequest<?> request) {
        if (!authFeatureFlags.isAuthenticationRequired()) {   // both auth flags OFF -> dev bypass
            return Mono.just(new ServerAuthentication("anonymous-bypass", List.of(), Map.of()));
        }
        String sessionId = SessionUtil.findSessionCookie(request);
        if (sessionId == null) return Mono.empty();
        return Mono.fromCallable(() -> sessionValidationService.validate(sessionId))
            .subscribeOn(Schedulers.boundedElastic())          // blocking JDBC off the event loop
            .mapNotNull(user -> new ServerAuthentication(
                user.getUuid().toString(), user.getRoles(), buildAttributes(user)));
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
}
```

Validation: expiry check → sliding-window "touch" → transparent OIDC token refresh:

```java
@Transactional
public AuthenticatedUser validate(String sessionId) {
    AuthenticatedUser user = userAdapter.findAuthenticatedUserBySessionId(parsed).orElse(null);
    if (user == null) return null;
    if (!SessionUtil.isStillValid(user.getSessionExpiration())) {          // absolute lifetime hit
        userAuthenticationPort.deleteByUserAuthenticationId(user.getUserAuthenticationId());
        return null;
    }
    if (!"OIDC".equals(user.getAuthMethod())) return touchAndReturn(user); // Smart-ID: no tokens
    if (SessionUtil.isStillValid(user.getTokenExpiration())) return touchAndReturn(user);
    return tokenRefreshService.refresh(user.getUserAuthenticationId())     // expired -> refresh
        .map(roles -> { user.setRoles(roles); return touchAndReturn(user); })
        .orElse(null);
}

private AuthenticatedUser touchAndReturn(AuthenticatedUser user) {
    // sliding window: every valid request pushes session_expiration forward by 12h (SessionPolicy)
    userAuthenticationPort.touchLastActivity(user.getUserAuthenticationId(), sessionPolicy.newAbsoluteExpiry());
    return user;
}
```

`TokenRefreshService` row-locks the session (`SELECT ... FOR UPDATE`, `REQUIRES_NEW`), calls Keycloak's token endpoint, terminates the session on `invalid_grant`, and on transient IdP failure keeps a still-valid token rather than logging the user out.

The login handler writes the internal session id (not a JWT) into the cookie, and works around a Micronaut 4.10.1 bug that mangles absolute `login-success` URLs:

```java
@Singleton
@Replaces(TokenCookieLoginHandler.class)
public class SessionCookieLoginHandler extends TokenCookieLoginHandler {
    @Override
    public List<Cookie> getCookies(Authentication authentication, HttpRequest<?> request) {
        String sessionId = (String) authentication.getAttributes().get(SessionUtil.SESSION_ID_ATTRIBUTE);
        Cookie authCookie = Cookie.of(SessionUtil.AUTH_COOKIE_NAME, sessionId);
        authCookie.configure(accessTokenCookieConfiguration, request.isSecure());
        return List.of(authCookie);
    }

    @Override
    public MutableHttpResponse<?> loginSuccess(Authentication authentication, HttpRequest<?> request) {
        // bypass RedirectService: it string-concatenates context-path onto absolute URLs
        String loginSuccess = redirectConfiguration.getLoginSuccess();
        MutableHttpResponse<?> response = loginSuccess == null ? HttpResponse.ok()
            : HttpResponse.status(HttpStatus.SEE_OTHER).headers(h -> h.location(URI.create(loginSuccess)));
        return applyCookies(response, getCookies(authentication, request));
    }
}
```

**Verify:** log in via the frontend, then `SELECT * FROM system.user_authentication;` — one row per logged-in user, `session_expiration` advancing on every request.

### 6.2 OIDC login (Keycloak)

**Files:** `auth/oidc/OidcAuthenticationMapper.java`, `keycloak/micronaut-realm.json`, `application.yml` (oauth2 block)

Standard authorization-code + PKCE; Micronaut handles the redirect dance (`/oauth/login/oidc`, `/oauth/callback/oidc`). The custom mapper turns validated IdP claims into an internal user + session:

```java
@Singleton
@Named("oidc")   // matches the oauth2 client name in application.yml
public class OidcAuthenticationMapper implements OpenIdAuthenticationMapper {
    @Override
    public Publisher<AuthenticationResponse> createAuthenticationResponse(
            String providerName, OpenIdTokenResponse tokenResponse, OpenIdClaims openIdClaims, State state) {
        if (authFeatureFlags.shouldRejectOidcLogin()) {
            return Mono.just(AuthenticationResponse.failure("oidc-auth-disabled"));
        }
        List<String> roles = extractRoles(openIdClaims);   // the "roles" ID-token claim - NOT scope!
        String ssn = generateEstonianSsn();                // demo placeholder for personal_code

        return Mono.fromCallable(() -> {
                AuthenticatedUser user = userService.loginWithOidc(
                    ssn, openIdClaims.getGivenName(), openIdClaims.getFamilyName(),
                    openIdClaims.getEmail(), roles, tokenResponse);
                Map<String, Object> attributes = new HashMap<>();
                attributes.put(SessionUtil.SESSION_ID_ATTRIBUTE, user.getSessionId().toString());
                return (AuthenticationResponse) AuthenticationResponse.success(
                    user.getUuid().toString(), user.getRoles(), attributes);
            })
            .subscribeOn(Schedulers.boundedElastic());     // loginWithOidc does blocking JDBC
    }

    private static List<String> extractRoles(OpenIdClaims claims) {
        Object rolesClaim = claims.getClaims().get("roles");
        return rolesClaim instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }
}
```

Two hard-won lessons baked into the above and the realm file:
1. **Roles come from the ID token's `roles` claim, not `tokenResponse.getScope()`** — scope is what the *client requested* (`"openid profile email"`), not what the user *is*. Reading scope gave every user zero real roles.
2. **Keycloak's client-role protocol mapper needs `usermodel.clientId`** telling it *which* client's roles to emit — without it the `roles` claim is silently empty:

```json
{ "name": "roles", "protocolMapper": "oidc-usermodel-client-role-mapper",
  "config": { "claim.name": "roles", "multivalued": "true", "id.token.claim": "true",
              "access.token.claim": "true", "usermodel.clientId": "micronaut-app" } }
```

Test users (realm auto-imported by compose via `--import-realm`; skipped if the realm already exists — then apply via Admin Console instead): `adminuser` / `headuser` / `testuser`, all `password123`, carrying client roles `example.admin` / `example.head-user` / `example.end-user`.

Logout: `OidcEndSessionLogoutHandler` + `ModernKeycloakEndSessionEndpoint` handle two Keycloak/Micronaut version mismatches (legacy issuer path detection; Keycloak 19+ dropping `redirect_uri` in favour of `post_logout_redirect_uri`); `LogoutService` best-effort revokes tokens.

**Verify:** log in as each user; `GET /user` must return non-empty `roleGroups` — empty means the realm mapper/roles weren't seeded.

### 6.3 Smart-ID login

**Files:** `auth/smartid/SmartIdAuthController.java`, `SmartIdClientFactory.java`, `SmartIdProperties.java`, `auth/user/service/SmartIdLoginService.java`

Two-step notification flow against SK's demo environment; pending sessions parked in a 5-minute Caffeine cache:

```java
@Override
public HttpResponse<SmartIdInitResponse> smartIdInit(@Valid SmartIdInitRequest request) {
    RpChallenge rpChallenge = RpChallengeGenerator.generate();
    String verificationCode = VerificationCodeCalculator.calculate(rpChallenge.value());   // PIN shown in app

    var builder = smartIdClientProvider.get().createNotificationAuthentication()
        .withSemanticsIdentifier(new SemanticsIdentifier(PNO, EE, request.getIdentityCode()))
        .withRpChallenge(rpChallenge.toBase64EncodedValue())
        .withCertificateLevel(AuthenticationCertificateLevel.QUALIFIED)
        .withInteractions(List.of(NotificationInteraction.displayTextAndPin("Logging in")));

    var sessionResponse = builder.initAuthenticationSession();
    String reference = UUID.randomUUID().toString();
    sessionCache.put(reference, new PendingSmartIdAuthentication(
        sessionResponse.sessionID(), builder.getAuthenticationSessionRequest()));
    return HttpResponse.ok(new SmartIdInitResponse().reference(reference).verificationCode(verificationCode));
}

@Override
public HttpResponse<Void> smartIdComplete(String reference) {
    var pending = sessionCache.get(reference, PendingSmartIdAuthentication.class);   // then invalidate
    SessionStatus status = smartIdClientProvider.get().getSessionStatusPoller()
        .fetchFinalSessionStatus(pending.get().sessionId());
    AuthenticationIdentity identity = validator.validate(status, pending.get().authenticationSessionRequest(), scheme);
    AuthenticatedUser user = smartIdLoginService.loginWithSmartId(identity);   // creates user, role "end-user"
    response.cookie(smartIdLoginService.buildAuthCookie(user.getSessionId(), secure));  // same authToken cookie
    return response;
}
```

Known gap: the SK trust-anchor root certificate isn't bundled — certificate validation fails until sourced from SK and wired into `SmartIdClientFactory`.

**Verify:** `POST /smart-id/init {"identityCode":"<11 digits>"}` → `{reference, verificationCode}`.

### 6.4 Feature-flag gating of auth methods

**File:** `auth/AuthFeatureFlags.java`

| `oidc-auth` | `smart-id-auth` | Effect |
|---|---|---|
| OFF | OFF | Auth bypassed entirely (dev only) — fetcher returns roleless `anonymous-bypass` |
| ON | OFF | Only OIDC; Smart-ID endpoints 403 |
| OFF | ON | Only Smart-ID; OIDC mapper returns failure |
| ON | ON | Both |

```java
public boolean isAuthenticationRequired() { return isOidcAuthEnabled() || isSmartIdAuthEnabled(); }
public boolean shouldRejectOidcLogin()    { return isAuthenticationRequired() && !isOidcAuthEnabled(); }
public boolean shouldRejectSmartIdLogin() { return isAuthenticationRequired() && !isSmartIdAuthEnabled(); }
```

The bypass user has **zero roles**, so even in bypass mode every role-gated write correctly 403s.

---

## 7. Authorization: role groups

**Files:** `auth/user/service/RoleGroupResolver.java`, `auth/security/RequiresRoleGroup.java`, `RequiresRoleGroupInterceptor.java`

Raw roles are free-form strings synced from the IdP; config buckets them into three groups:

```yaml
app:
  roles:
    admin-roles:     [ example.admin ]
    head-user-roles: [ example.head-user ]
    end-user-roles:  [ example.end-user, end-user ]   # "end-user" = Smart-ID default
```

```java
@Singleton
public class RoleGroupResolver {
    public List<UserInfoOutputModalRoleGroupsInner> resolve(List<String> roles) {
        List<UserInfoOutputModalRoleGroupsInner> groups = new ArrayList<>();
        if (roles.stream().anyMatch(adminRoles::contains))    groups.add(ADMIN);
        if (roles.stream().anyMatch(headUserRoles::contains)) groups.add(HEAD_USER);
        if (roles.stream().anyMatch(endUserRoles::contains))  groups.add(END_USER);
        return groups;
    }
}
```

Endpoint protection is a small custom AOP annotation (Micronaut has no built-in role-*group* concept):

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@InterceptorBinding(kind = InterceptorKind.AROUND)
@Type(RequiresRoleGroupInterceptor.class)
public @interface RequiresRoleGroup {
    UserInfoOutputModalRoleGroupsInner[] value();   // reuses the generated enum - no duplicate type
}
```

```java
@Singleton
@InterceptorBean(RequiresRoleGroup.class)
public class RequiresRoleGroupInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Set<UserInfoOutputModalRoleGroupsInner> allowed =
            Set.of(context.synthesize(RequiresRoleGroup.class).value());
        List<String> rawRoles = securityService.getAuthentication()
            .map(a -> new ArrayList<>(a.getRoles())).orElseGet(ArrayList::new);
        if (roleGroupResolver.resolve(rawRoles).stream().noneMatch(allowed::contains)) {
            // same exception @Secured throws internally -> framework maps it to 403, no handler needed
            throw new AuthorizationException(securityService.getAuthentication().orElse(null));
        }
        return context.proceed();
    }
}
```

The matrix (reads need only `isAuthenticated()`; groups are **not** hierarchical — ADMIN is listed explicitly wherever it's allowed):

| Action | Groups |
|---|---|
| Create person / car(+issuer) | END_USER, ADMIN |
| Assign car owner / create insurance | HEAD_USER, ADMIN |
| Any update / delete | ADMIN |
| Upload/search documents | any authenticated |

**Verify:** as `testuser` (END_USER): `POST /cars` → 200, `POST /insurances` → 403.

---

## 8. Feature flags (Unleash)

**Files:** `config/UnleashFactory.java`, `business/useCase/GetPersons.java` (example usage)

```java
@Factory
public class UnleashFactory {
    @Bean @Singleton
    public Unleash unleash() {
        return new DefaultUnleash(UnleashConfig.builder()
            .appName(appName).unleashAPI(apiUrl)
            .customHttpHeader("Authorization", apiToken)
            .build());
    }
}
```

```java
// usage - one line at the top of a use case
if (!unleash.isEnabled("get-persons")) {
    return List.of();
}
```

| Flag | Gates |
|---|---|
| `get-persons` | `GET /persons` returns data vs empty list |
| `persons-table` | frontend Persons table renders at all |
| `oidc-auth` / `smart-id-auth` | auth methods (§6.4) |

**The trap:** flags are *not code* — a fresh Unleash instance has none of them, and a missing flag silently reads as `false`. A flag-gated feature "doing nothing" on a new environment means the flag was never created (admin UI at `:4242`, `unleash`/`unleash`).

**Verify:** toggle `get-persons` off → `GET /persons` returns `[]`; on → data.

---

## 9. Full-text search (GIN indexes)

### 9.1 The indexes

**File:** `changelog/structure/15-search-indexes.sql` (and `17-` for documents)

One GIN index per table over the concatenated `tsvector` of its searchable columns — not one index per column:

```sql
--changeset sander:add-person-search-index
CREATE INDEX person__combined_search__fts_idx ON person.person USING GIN ((
    to_tsvector('simple', coalesce(name, '')) ||
    to_tsvector('simple', coalesce(nickname, '')) ||
    to_tsvector('simple', coalesce(identity_code, ''))
));
```

`'simple'` config: lowercase + split, no stemming (there is no Estonian stemmer) — searches match exact tokens only. The query side must generate **the same expression** or Postgres won't use the index.

### 9.2 TextSearchBuilder & SortMapper

**Files:** `util/search/TextSearchBuilder.java`, `business/global/SortMapper.java`

```java
public static Condition buildCondition(String input, List<Field<String>> fields) {
    if (StringUtils.isEmpty(input) || fields.isEmpty()) return DSL.noCondition();
    String trimmed = input.trim();

    if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
        String phrase = trimmed.substring(1, trimmed.length() - 1);           // "quoted" -> phrase match
        return DSL.condition("{0} @@ phraseto_tsquery('simple', {1})", combinedTsVector(fields), DSL.val(phrase));
    }
    if (trimmed.contains("*")) {                                              // wildcard -> LIKE fallback
        String like = trimmed.replace('*', '%');
        return fields.stream().<Condition>map(f -> f.likeIgnoreCase(like))
            .reduce(Condition::or).orElseGet(DSL::noCondition);
    }
    return DSL.condition("{0} @@ plainto_tsquery('simple', {1})", combinedTsVector(fields), DSL.val(trimmed));
}

private static Field<Object> combinedTsVector(List<Field<String>> fields) {
    // MUST mirror the index expression: to_tsvector('simple', coalesce(col, '')) || ...
    return fields.stream()
        .<Field<Object>>map(f -> DSL.field("to_tsvector('simple', coalesce({0}, ''))", Object.class, f))
        .reduce((l, r) -> DSL.field("{0} || {1}", Object.class, l, r))
        .orElseThrow();
}
```

```java
// SortMapper - frontend sends "field:asc"; allow-list per entity prevents sort-by-anything
public static List<OrderField<?>> mapSortFields(List<String> sort, Map<String, Field<?>> sortableFields) { ... }
// usage: SortMapper.mapSortFields(sort, Map.of("mark", CAR_.MARK, "model", CAR_.MODEL))
```

### 9.3 Detailed search

**Files:** `business/search/PersonSearchCriteriaMapper.java` (+ Car/Insurance/IssuerFirm variants)

Structured filters AND-composed; cross-entity filters resolve via subqueries so the main query never row-multiplies:

```java
public Condition buildCondition(PersonSearchRequest request) {
    Condition condition = DSL.noCondition();
    if (StringUtils.isNotEmpty(request.getName()))
        condition = condition.and(PERSON_.NAME.containsIgnoreCase(request.getName()));
    if (StringUtils.isNotEmpty(request.getCarMark()) || StringUtils.isNotEmpty(request.getCarModel()))
        condition = condition.and(PERSON_.ID.in(carOwnerIdsMatching(request)));          // subquery on car
    if (StringUtils.isNotEmpty(request.getIssuerFirmName()))
        condition = condition.and(PERSON_.ID.in(issuerFirmOwnerIdsMatching(...)));       // car JOIN issuer_firm
    if (StringUtils.isNotEmpty(request.getTextSearch()))
        condition = condition.and(TextSearchBuilder.buildCondition(request.getTextSearch(),
            List.of(PERSON_.NAME, PERSON_.NICKNAME, PERSON_.IDENTITY_CODE)));            // GIN
    return condition;
}
```

### 9.4 Quick search (cross-entity UNION ALL)

**Files:** `business/search/QuickPersonSearchCriteriaMapper.java`, `business/useCase/QuickSearchPersons.java`, `controller/QuickSearchController.java`

One search box that finds *persons* by anything related to them — their own fields, their cars, those cars' issuer firms, their insurances. Each table is searched against **its own** GIN index; matching person-ids are `UNION ALL`ed; the main query filters by the id set. Five index scans instead of one giant join:

```java
public Condition getQuickSearchCondition(String input) {
    Select<Record1<Long>> unionedPersonIds = buildPersonFieldsQuery(input)
        .unionAll(buildCarFieldsQuery(input))          // select CAR_.OWNER_ID ... where owner_id is not null
        .unionAll(buildIssuerFirmFieldsQuery(input))   // issuer_firm JOIN car -> owner_id
        .unionAll(buildInsuranceFieldsQuery(input));   // select INSURANCE_.PERSON_ID

    Select<Record1<Long>> combined = DSL
        .selectDistinct(DSL.field(PERSON_.ID.getName(), Long.class))
        .from(unionedPersonIds.asTable("person_ids"));

    return PERSON_.ID.in(combined);
}
```

**Verify:** `GET /persons/quick/search?searchText=Toyota` returns the person who *owns* a Toyota, though "Toyota" appears nowhere on the person row.

---

## 10. Semantic search (pgvector + embeddings)

> Concepts (what embeddings/HNSW/RAG are): [`search-explained.md`](search-explained.md). Extensions (hybrid endpoint, full RAG): [`hybrid-search-and-rag-example.md`](hybrid-search-and-rag-example.md).

### 10.1 Infrastructure: pgvector & Ollama

**Files:** `.env`, `docker-compose.yaml`, `.github/workflows/backend-ci.yml`, `application.yml`

Plain `postgres` has no `vector` type — swap the image (drop-in; existing volume survives):

```bash
# .env
PG_IMAGE=pgvector/pgvector:pg17
EMBEDDING_URL=http://localhost:11434
EMBEDDING_MODEL=bge-m3
```

```yaml
# docker-compose.yaml
  local-db:
    image: ${PG_IMAGE}
  ollama:
    image: ollama/ollama
    ports: ["11434:11434"]
    volumes: [ollama-models:/root/.ollama]
```

```bash
docker exec -it micronaut-out-of-the-box-template-ollama-1 ollama pull bge-m3   # once, ~1.2GB, 1024-dim, multilingual
```

```yaml
# application.yml - 60s: Ollama cold-loads the model after idle
micronaut:
  http:
    services:
      embedding:
        url: "${EMBEDDING_URL:`http://localhost:11434`}"
        read-timeout: 60s
embedding:
  model: ${EMBEDDING_MODEL:bge-m3}
```

Vector DDL is context-gated so jOOQ codegen (plain PG image, `--contexts=jooq`) never sees it:

```sql
-- 18-document-vector.sql - every changeset carries context:!jooq
--changeset sander:add-pgvector-extension context:!jooq
CREATE EXTENSION IF NOT EXISTS vector;

--changeset sander:add-document-embedding-column context:!jooq
ALTER TABLE document.document ADD COLUMN embedding vector(1024);   -- dims = model output size

--changeset sander:add-document-embedding-index context:!jooq
CREATE INDEX document__embedding__hnsw_idx ON document.document USING hnsw (embedding vector_cosine_ops);
```

Consequence: jOOQ never generates the `embedding` column → all vector access is raw SQL with `?::vector` binds.

### 10.2 The document store & row vectorization

**Files:** `16/17-document-*.sql`, `business/embedding/EmbeddingClient.java`, `business/useCase/CreateDocument.java`, `business/adapter/DocumentAdapter.java`

Table: `document.document(id, title, content JSONB, content_text TEXT, created_at [, embedding])` — `content` is the parseable source of truth (`{"title", "paragraphs":[...]}`), `content_text` the flattened text (GIN target + embedding source + preview).

```java
// EmbeddingClient - Ollama POST /api/embed; failures degrade to Optional.empty()
public Optional<float[]> embed(String text) {
    try {
        EmbedResponse response = httpClient.toBlocking().retrieve(
            HttpRequest.POST("/api/embed", new EmbedRequest(model, List.of(text))), EmbedResponse.class);
        ...
    } catch (Exception e) {
        LOG.warn("Embedding service unavailable ({}); proceeding without embedding", e.getMessage());
        return Optional.empty();
    }
}
public static String toVectorLiteral(float[] v) { /* -> "[0.1,0.2,...]" */ }
```

Insert flow — embed **before** the transaction (remote HTTP call must not hold a connection), then insert + raw vector UPDATE atomically:

```java
// CreateDocument.execute (not @Transactional itself)
Optional<float[]> embedding = embeddingClient.embed(title + "\n\n" + contentText);
Document created = documentAdapter.create(document, embedding.map(EmbeddingClient::toVectorLiteral).orElse(null));
return new Result(created, embedding.isPresent());   // embedded:false surfaces in the API

// DocumentAdapter
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

⚠️ `DocumentController` is `@ExecuteOn(TaskExecutors.BLOCKING)` — Micronaut hard-rejects blocking HTTP client calls on Netty event-loop threads (real bug hit here: uploads silently stored `embedded:false` until the annotation was added).

### 10.3 The context-search endpoint

**Files:** `business/useCase/ContextSearchDocuments.java`, `DocumentAdapter.contextSearch`

Embed the *query*, then nearest-neighbour by cosine distance (`<=>`), similarity = 1 − distance:

```java
public List<ContextRow> contextSearch(String vectorLiteral, int limit) {
    return dsl.resultQuery(
            "SELECT id, title, content_text, created_at, 1 - (embedding <=> ?::vector) AS similarity "
                + "FROM document.document WHERE embedding IS NOT NULL "
                + "ORDER BY embedding <=> ?::vector LIMIT ?",
            vectorLiteral, vectorLiteral, limit)
        .fetch(DocumentAdapter::toContextRow);
}
```

Results are **ranked with scores**, never a hard match set. Verified behaviour on this repo's test corpus — query `hunt sööb lammast`:

| similarity | document |
|---|---|
| 0.92 | "hunt sööb lammast" |
| 0.52 | "kriimsilm sööb utte" |
| 0.49 | "võsavillem näksib voona" |
| 0.27 | unrelated insurance memo |

Keyword search (`?keyword=lammast`) meanwhile returns only the literal match — the two engines answering their two different questions.

**Verify:** upload docs via `POST /documents`, then `GET /documents/context-search?query=...` (send UTF-8 from a script, not a Windows console) and `GET /documents/search?keyword=...`.

---

## 11. WebSockets

**Files:** `controller/PersonsWebSocket.java`, `controller/InsuranceWebSocket.java`

Two endpoints, two patterns:

```java
// /ws/persons - plain keepalive + broadcast scaffold
@ServerWebSocket("/ws/persons")
public class PersonsWebSocket {
    @OnMessage
    public void onMessage(String message, WebSocketSession session) {
        if ("ping".equals(message)) session.sendSync("pong");
    }
    public void broadcastUpdate() { broadcaster.broadcastSync("UPDATE"); }
}
```

```java
// /ws/insurance - every inbound message (incl. the heartbeat ping) gets live DATA back, not a pong
@ServerWebSocket("/ws/insurance")
public class InsuranceWebSocket {
    @OnMessage
    public void onMessage(String message, WebSocketSession session) {
        session.sendSync(currentSnapshot());               // Micronaut serializes the List to JSON
    }
    public void broadcastUpdate() { broadcaster.broadcastSync(currentSnapshot()); }

    private List<Insurance> currentSnapshot() {
        return InsuranceMapper.mapToInsurances(getExpiringSoonInsurances.execute());  // reads the SQL view
    }
}
```

`InsuranceController` calls `broadcastUpdate()` after every create/update/delete, so connected clients refresh instantly; the client's 10s ping covers drift in between. Frontend note: vueuse's `heartbeat` option can't be used for this socket (it pattern-matches a literal pong string) — the Vue app drives the ping manually and treats every frame as data.

The "expiring soon" data source is a live SQL view, not a maintained table (Postgres can't use `CURRENT_DATE` in a stored generated column):

```sql
CREATE VIEW insurance.insurance_expiring_soon AS
SELECT i.*, (i.expiry_date - CURRENT_DATE) AS days_left
FROM insurance.insurance i
WHERE (i.expiry_date - CURRENT_DATE) < 30;
```

**Verify:** connect to `ws://127.0.0.1:8080/ws/insurance`, send `ping` → JSON array of expiring insurances.

---

## 12. CI: GitHub Actions

**File:** `.github/workflows/backend-ci.yml`

Build + test on every push to `main` / every PR — deliberately no image build or deploy:

```yaml
jobs:
  build-and-test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: pgvector/pgvector:pg17     # NOT plain postgres: 18-document-vector.sql needs the extension
        env: { POSTGRES_DB: thedb, POSTGRES_USER: postgres, POSTGRES_PASSWORD: postgres }
        ports: [ "5432:5432" ]
        options: >-
          --health-cmd "pg_isready -U postgres" --health-interval 10s --health-timeout 5s --health-retries 5
    steps:
      - uses: actions/checkout@v4
      - uses: gradle/actions/wrapper-validation@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@v4
      - run: chmod +x gradlew
      - run: ./gradlew :database:update --no-daemon    # migrations vs a real PG (matches .env creds)
      - run: ./gradlew generateJooq --no-daemon        # second migration replay, from-scratch DB
      - run: ./gradlew build --no-daemon
```

The two Liquibase runs are the point: `:database:update` exercises a persistent-DB path, `generateJooq` a from-scratch one — together the strongest available regression net for migration-ordering bugs (§2). Docker is preinstalled on GitHub runners, so Testcontainers just works. The committed `.env` (placeholder credentials only) is what lets `:database:update` connect with zero configuration.

**Verify:** push a branch → Actions tab → all steps green.

---

## 13. Further reading

| Doc | Contents |
|---|---|
| [`backend-tech-solution.md`](backend-tech-solution.md) | Architecture reference: every component, schema, endpoint, config var, known gaps |
| [`search-explained.md`](search-explained.md) | GIN/tsvector, embeddings, HNSW, RAG — layman-first explanations |
| [`hybrid-search-and-rag-example.md`](hybrid-search-and-rag-example.md) | Copy-paste walkthrough: hybrid keyword+context endpoint; full RAG (A+G) with a local chat model |
| [`full-text-search-guide (1).md`](<full-text-search-guide (1).md>) | The original GIN/UNION-ALL search pattern this project's search follows |
| [`kubernetes-backend-implementation.md`](kubernetes-backend-implementation.md) | Deploying all of this to a real, $0 cloud Kubernetes (OKE) |
