## Micronaut Template

Base Micronaut backend template with:
- jOOQ database codegen
- OpenAPI server codegen
- Structured logging
- Unleash feature flags
- OIDC login via Keycloak
- Smart-ID login (Estonian eID)

## Prerequisites

Docker and Docker Compose.

## 1. Start infrastructure

Starts Postgres, Unleash, and Keycloak.
```bash
docker compose up -d
```

## 2. Database

Apply the structure/data changesets.
```bash
./gradlew :database:update
```

Generate jOOQ classes from the schema.
```bash
./gradlew generateJooq
```

Re-apply changesets after every jOOQ generation.
```bash
./gradlew :database:update
```

## 3. OpenAPI

Spec lives in `openapi/src/main/resources/swagger/openapi.yml`, example usage in `src/main/java/com/example/business`.

Generate the server API interfaces.
```bash
./gradlew :openapi:generateServerOpenApiApis
```

Generate the request/response models.
```bash
./gradlew :openapi:generateServerOpenApiModels
```

## 4. Run the app

`.env` is loaded automatically and exported as real environment variables to the run task.
```bash
./gradlew run
```

The API is served at `http://localhost:8080/api`.

## Unleash (feature flags)

Open the admin UI.
```
http://localhost:4242
```

Log in.
```
user: unleash
password: unleash
```

Create a flag, then toggle it ON for the `development` environment (that's the environment the app's client token is scoped to).

| Flag | ON | OFF |
|---|---|---|
| `get-persons` | `GET /api/persons` returns data | returns an empty list |
| `oidc-auth` | OIDC login is enforced | rejected only if `smart-id-auth` is ON (forces the other method); bypassed if both are OFF |
| `smart-id-auth` | Smart-ID login is enforced | rejected only if `oidc-auth` is ON (forces the other method); bypassed if both are OFF |

If both `oidc-auth` and `smart-id-auth` are OFF, every request — including login itself — bypasses authentication entirely. Local development only — never leave both off in production.

`get-persons` is referenced in `src/main/java/com/example/business/useCase/GetPersons.java`.

<img src="unleash_ftr_flag.png" alt="unleash feature flag view">
<img src="ftr_flag_output_in_logs.png" alt="feature flag output logs">

## Keycloak (OIDC login)

Keycloak auto-imports the `micronaut` realm from `keycloak/micronaut-realm.json` on every startup (`--import-realm`, skips realms that already exist).

Admin console.
```
http://localhost:8081/admin
```
```
user: admin
password: admin
```

Test users (password for both: `password123`).
```
testuser   - regular user
adminuser  - admin role
```

Start a login (requires the `oidc-auth` Unleash flag to be ON, see above).
```
http://localhost:8080/api/oauth/login/oidc
```

Check what the app resolved its OIDC config to, useful when `.env` doesn't seem to be picked up.
```
http://localhost:8080/api/debug/oidc-config
```

To change users, roles, or redirect URIs: edit `keycloak/micronaut-realm.json` and recreate the container.
```bash
docker compose up -d --force-recreate keycloak
```

## Smart-ID login

Wired against SK's public demo environment (`relying-party-uuid: 00000000-0000-4000-8000-000000000000`).

Known gap: the bundled `smart-id-java-client` jar doesn't ship SK's trust-anchor root certificate, so certificate validation fails until it's sourced from SK's trust documentation and wired into `SmartIdClientFactory`.
```
https://sk-eid.github.io/smart-id-documentation/https_pinning.html
```
