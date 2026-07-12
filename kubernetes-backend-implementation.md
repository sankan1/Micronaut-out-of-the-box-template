# Deploying the Micronaut Backend to Kubernetes — for $0, on a real cloud

This is a from-zero walkthrough for taking **this specific repo** — a Java 21 / Micronaut 4.6 (Netty) backend, multi-module Gradle build, jOOQ + Liquibase + Postgres, Keycloak OIDC, Unleash feature flags — and running it on a **real, public, managed Kubernetes service**, reachable from your frontend at a real URL, without spending any money.

> **Scope decision, explicitly:** this guide optimizes for two constraints together — *not* self-hosted (no "runs on my laptop" / tunnel-to-home-PC tricks as the primary path) **and** genuinely $0, indefinitely, not a time-limited trial credit that quietly starts billing you later. As of 2026, there is exactly one major cloud provider whose free tier satisfies both: **Oracle Cloud Infrastructure (OCI)**. GCP/AWS/Azure all have free *trials* (one-time credit, 30–90 days, or 12-month-limited allowances) but none of them waive the ongoing cost of a managed Kubernetes control plane *and* compute *and* a load balancer the way OCI's Always Free tier does. That comparison is laid out in full in §5 so you can see exactly why, rather than just taking it on faith.

## Table of Contents

1. [Architecture: what you're building](#1-architecture-what-youre-building)
2. [Pre-flight: config this repo needs at deploy time](#2-pre-flight-config-this-repo-needs-at-deploy-time)
3. [Prerequisites](#3-prerequisites)
4. [Containerize the backend](#4-containerize-the-backend)
5. [Why Oracle Cloud, specifically — the honest cost comparison](#5-why-oracle-cloud-specifically--the-honest-cost-comparison)
6. [Set up the OCI account correctly (the part that prevents surprise bills)](#6-set-up-the-oci-account-correctly-the-part-that-prevents-surprise-bills)
7. [Provision a free OKE cluster](#7-provision-a-free-oke-cluster)
8. [Push the image to GHCR (free)](#8-push-the-image-to-ghcr-free)
9. [Namespace, ConfigMap, Secret](#9-namespace-configmap-secret)
10. [Deploy Postgres in-cluster](#10-deploy-postgres-in-cluster)
11. [Deploy the backend](#11-deploy-the-backend)
12. [Install an Ingress controller (using OCI's free Load Balancer)](#12-install-an-ingress-controller-using-ocis-free-load-balancer)
13. [Get a real hostname for $0](#13-get-a-real-hostname-for-0)
14. [HTTPS with cert-manager + Let's Encrypt](#14-https-with-cert-manager--lets-encrypt)
15. [Production config: CORS, cookies, redirects](#15-production-config-cors-cookies-redirects)
16. [Wire up the frontend](#16-wire-up-the-frontend)
17. [Verify end-to-end + troubleshooting](#17-verify-end-to-end--troubleshooting)
18. [Optional: Keycloak & Unleash in-cluster](#18-optional-keycloak--unleash-in-cluster)
19. [Staying within Always Free (no teardown required)](#19-staying-within-always-free-no-teardown-required)
20. [Checklist summary](#20-checklist-summary)
21. [Resources & References](#21-resources--references)

---

## 1. Architecture: what you're building

```
                          Internet
                             │
              Free hostname: api.yourname.duckdns.org
                       (or your own domain)
                             │
                             ▼
                 ┌─────────────────────┐
                 │  Ingress Controller │  (ingress-nginx, backed by
                 │  + cert-manager TLS │   OCI's free Flexible LB)
                 └──────────┬──────────┘
                             │ routes by Host header
                             ▼
                 ┌─────────────────────┐
                 │  Service: backend   │  ClusterIP, port 80→8080
                 └──────────┬──────────┘
                             ▼
                 ┌─────────────────────┐
                 │ Deployment: backend │  N pods, your Docker image
                 │ (Micronaut/Netty)   │  (image lives on GHCR, free)
                 └──────────┬──────────┘
                             ▼
                 ┌─────────────────────┐
                 │ StatefulSet:postgres│  PVC on OCI's free block storage
                 └─────────────────────┘

      Cluster: OKE Basic (free control plane) on Always-Free
      Ampere A1 worker nodes — every box on this diagram is $0.
```

Same Kubernetes objects as any cluster, and why each exists:

| Object | Purpose |
|---|---|
| **Namespace** | Isolates everything for this app from other things you might run in the same (free) cluster later. |
| **ConfigMap** | Non-secret config (hostnames, flags) as env vars — change without rebuilding the image. |
| **Secret** | Same idea, but for credentials (DB password, OIDC client secret) — base64-at-rest, RBAC-restricted. |
| **Deployment** | Declares "I want N pods running this image" and handles rolling restarts/replacement of crashed pods. |
| **StatefulSet** (Postgres) | Like a Deployment but gives the pod a stable identity + a PersistentVolumeClaim that survives pod restarts — needed for a database. |
| **Service** | Stable in-cluster DNS name + IP that load-balances across the Deployment's pods, even as they're replaced. |
| **Ingress** + **Ingress Controller** | The thing that actually owns a public IP and routes `Host: api.yourname.duckdns.org` requests to the right Service. Kubernetes ships the *Ingress API* but not a controller — you install one (ingress-nginx). |
| **ClusterIssuer** (cert-manager) | Automates proving domain ownership to Let's Encrypt and rotating the TLS certificate. |

---

## 2. Pre-flight: config this repo needs at deploy time

Before touching Kubernetes, three things specific to **this** codebase will bite you in a container if you don't account for them. None require editing the source — they're all environment-variable overrides, same pattern the repo already uses for `.env`.

### 2.1 `micronaut.server.host: localhost` — the one that will actually break things

[`src/main/resources/application.yml:7`](src/main/resources/application.yml#L7) hardcodes:

```yaml
micronaut:
  server:
    host: localhost
```

This makes Netty bind **only** to the loopback interface. That's fine when you run `./gradlew run` on your own machine, but inside a container, requests arrive on the container's network interface, not loopback — so with this setting, **the readiness probe, the Service, and the Ingress will all get connection-refused**, even though `kubectl get pods` shows the pod as `Running`.

Micronaut's environment-variable property source has higher precedence than `application.yml`, so you don't need to touch the file — just set this env var on the container:

```
MICRONAUT_SERVER_HOST=0.0.0.0
```

This is set in the Deployment manifest in [§11](#11-deploy-the-backend). If you forget it, the symptom is: pod `Running`, but `readinessProbe`/`livenessProbe` never succeed, and `kubectl logs` shows Netty started fine.

### 2.2 No health endpoint exists yet

There's no `micronaut-management` dependency in [`build.gradle`](build.gradle), so there's no `/health` endpoint to point a real liveness/readiness probe at. The guide below uses a **TCP socket probe** on port 8080 as a zero-code-change stand-in — it confirms Netty is accepting connections, which is most of what you want for a demo/practice deployment.

If you want to do this properly later, add:

```groovy
// build.gradle
implementation 'io.micronaut:micronaut-management'
```

```yaml
# application.yml
endpoints:
  health:
    enabled: true
    sensitive: false

micronaut:
  security:
    intercept-url-map:
      - pattern: /health
        access:
          - isAnonymous()
```

...then switch the probes in §11 from `tcpSocket` to `httpGet: { path: /health, port: 8080 }`.

### 2.3 Env vars that change between "local docker-compose" and "production K8s"

These already exist as `${VAR:default}` placeholders in `application.yml`, so this is just "what to set differently," covered in detail in [§15](#15-production-config-cors-cookies-redirects):

| Var | Local (`.env`) | Production K8s |
|---|---|---|
| `APP_DOMAIN` | `http://localhost:9000` | `https://app.yourname.duckdns.org` (your frontend's real origin) |
| `COOKIE_SECURE` | `false` | `true` (cookie is rejected by browsers over HTTP otherwise) |
| `PG_HOST` | `localhost` | `postgres` (the in-cluster Service name, see §10) |
| `OIDC_ISSUER_URL` | local Keycloak | wherever Keycloak actually lives in prod (§18) |

---

## 3. Prerequisites

Install locally:

| Tool | Why |
|---|---|
| [Docker Desktop](https://docs.docker.com/desktop/) | build the image (also already required for local dev / Testcontainers in this repo) |
| [`kubectl`](https://kubernetes.io/docs/tasks/tools/) | talk to any Kubernetes cluster |
| [`helm`](https://helm.sh/docs/intro/install/) | install ingress-nginx and cert-manager without hand-writing hundreds of lines of YAML |
| [`oci` CLI](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/cliinstall.htm) | Oracle Cloud's CLI — optional (the Console's Quick Create wizard in §7 needs no CLI at all), but handy for scripting |
| A free OCI account | §6 — needs a card or PayPal for identity verification, will not be charged while you stay within Always Free limits |
| A free DuckDNS (or similar) hostname, or a domain you already own | §13 — no purchase required |

You do **not** need a credit card charged anywhere in this guide, and you do **not** need `minikube`/`kind` — this guide deploys to a real cloud cluster, not a local one.

On Windows, run all `kubectl`/`helm`/`oci` commands from PowerShell or Git Bash; both work identically here.

---

## 4. Containerize the backend

> **Relationship to CI:** `.github/workflows/backend-ci.yml` runs build+test (compile, apply migrations, regenerate jOOQ, package) on every push/PR — see `backend-tech-solution.md` §12. It deliberately stops there: no image build, no push, no deploy. This section's `dockerBuild`/`dockerPush` are currently manual steps; wiring them into a second GitHub Actions job (gated to pushes on `main`, pushing to GHCR with the workflow's built-in `GITHUB_TOKEN` — see §8, no extra secrets needed) is the natural next addition if this becomes a real CD pipeline rather than a manual deploy.

### 4.1 Recommended: use the Micronaut Gradle plugin's built-in Docker support

[`build.gradle`](build.gradle) already applies `io.micronaut.application` (`alias(libs.plugins.micronaut.application)`). That plugin ships Docker integration for free — no Dockerfile to hand-write or maintain. It reads `java { sourceCompatibility }` (21 here) and picks a matching JRE base image automatically.

```bash
./gradlew dockerBuild
```

This:
1. Builds the fat/shadow jar (`shadowJar`, auto-applied by the Micronaut plugin) — output at `build/libs/*-all.jar`.
2. Generates a Dockerfile at `build/docker/Dockerfile` (inspect it if curious: `./gradlew dockerfile`).
3. Builds a local image, by default tagged from `rootProject.name` + `version` (here roughly `micronaut-out-of-the-box-template:1.0.0` — confirm with `docker images` after the build, naming can vary slightly by plugin version).

To control the tag explicitly (you'll want this for pushing to GHCR in §8), add to `build.gradle`:

```groovy
tasks.named("dockerBuild") {
    images = ["ghcr.io/<YOUR_GITHUB_USERNAME>/micronaut-template:1.0.0"]
}
```

**Important for this repo specifically:** `src/generated/**` (jOOQ DAOs/POJOs from `generateJooq`, OpenAPI models) is already committed — it's not in `.gitignore`. That's good news here: `dockerBuild` only needs to *compile* the jar, and `compileJava` doesn't trigger `generateJooq` (only the OpenAPI codegen is wired as a `compileJava` dependency, per [`build.gradle:123`](build.gradle#L123)). So the Docker build won't try to spin up Testcontainers/Docker-in-Docker for jOOQ generation. **But** if you change the DB schema or the OpenAPI spec, you must run `./gradlew generateJooq` / `:openapi:generateServerOpenApiModels` locally and commit the regenerated sources *before* building the image — otherwise you're shipping a stale jar. (`.github/workflows/backend-ci.yml` already regenerates and rebuilds on every PR, so a forgotten regeneration shows up as a CI failure before it ever reaches this step.)

### 4.2 Alternative: a hand-written multi-stage Dockerfile

Useful to understand what's happening under the hood, or if you want a Dockerfile checked into the repo rather than generated at build time:

```dockerfile
# syntax=docker/dockerfile:1
FROM gradle:8-jdk21 AS build
WORKDIR /home/app
COPY . .
RUN ./gradlew clean shadowJar -x test --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /home/app/build/libs/*-all.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Same caveat applies: this does **not** run `generateJooq`, it just compiles against whatever is already committed under `src/generated/`.

```bash
docker build -t ghcr.io/<YOUR_GITHUB_USERNAME>/micronaut-template:1.0.0 .
```

### 4.3 Test the image locally before going anywhere near Kubernetes

Use your existing `docker-compose.yaml` Postgres as the dependency, and override the bind-host issue from §2.1:

```bash
docker compose up -d local-db
docker run --rm -p 8080:8080 \
  --add-host=host.docker.internal:host-gateway \
  -e MICRONAUT_SERVER_HOST=0.0.0.0 \
  -e PG_HOST=host.docker.internal \
  -e PG_PORT=5432 \
  -e PG_DATABASE=<your PG_DATABASE from .env> \
  -e PG_USERNAME=<your PG_USERNAME from .env> \
  -e PG_PASSWORD=<your PG_PASSWORD from .env> \
  -e OIDC_ENABLED=false \
  ghcr.io/<YOUR_GITHUB_USERNAME>/micronaut-template:1.0.0
```

Then `curl http://localhost:8080/persons` (with `oidc-auth`/`smart-id-auth` Unleash flags both OFF, auth is bypassed entirely — useful for this smoke test, see `backend-tech-solution.md` §6.1). If this works, the image is good and any later K8s failure is a Kubernetes config issue, not an app/image issue — a useful debugging split.

---

## 5. Why Oracle Cloud, specifically — the honest cost comparison

| Provider | Managed K8s control plane | Compute for nodes | Load Balancer | Net result for "real cloud, $0, indefinitely" |
|---|---|---|---|---|
| **OCI (OKE Basic)** | **Free**, forever | Always Free Ampere A1: 2 OCPU/12GB (free-tier account) or 4 OCPU/24GB (Pay-As-You-Go account staying within Always Free limits — still $0, see §6) | Free Flexible LB, 10Mbps | **Genuinely free indefinitely** |
| GKE | Free for one zonal cluster's management fee | Nodes billed as normal Compute Engine VMs — the Compute Engine "Always Free" e2-micro (1 instance, US regions only) is the only $0 node option, and it's too small for this stack alongside anything else | Billed | Free trial credit (~$300/90 days) covers it temporarily, then bills |
| EKS | **Always billed** (~$0.10/hr ≈ $73/mo for the control plane, no free tier) | Billed | Billed | Never free |
| AKS | Free control plane (Free SKU) | Nodes billed as VMs — the free-account 12-month B1s allowance is temporary, not indefinite | Billed | Free for new accounts' first 12 months only, then bills |

This is also why the earlier draft of this guide used DigitalOcean: it's cheap and simple, but it is **not free** — every option above except OCI either costs money immediately or starts costing money once a trial window closes. If "I don't want to pay anything, ever" is the actual constraint, OCI's Always Free tier is the only major-cloud answer as of 2026.

---

## 6. Set up the OCI account correctly (the part that prevents surprise bills)

1. Sign up at [oracle.com/cloud/free](https://www.oracle.com/cloud/free/). You'll be asked for a credit card or PayPal account — this is identity verification only; Oracle states Always Free resources are never billed, though a temporary $1–5 authorization hold may appear and disappear. **Pick your home region carefully when prompted — it cannot be changed later without a new account.** US East (Ashburn) and US West (Phoenix) tend to have the most consistently available free Ampere capacity; Frankfurt and Singapore are also reported as reliable.

2. **Strongly recommended: upgrade from "Free Tier" to "Pay As You Go" account type**, via the Console banner or *Account Management → Upgrade and Manage Payment*. This sounds counter to "I don't want to pay anything" but it isn't — it changes which *account class* you're billed under, not whether you get billed. Concretely, upgrading:
   - Restores the full **4 OCPU / 24GB** Always Free Ampere allowance (free-tier-only accounts were cut to 2 OCPU/12GB in June 2026).
   - Gives your capacity requests **standard priority** instead of free-tier-lowest-priority, which is the actual fix for the well-known "Out of host capacity" error when creating an A1 instance.
   - You are billed **$0** for anything that stays within Always Free limits — you'd only ever be charged if you provisioned something *outside* the free allotment, which nothing in this guide does.

3. Note your **tenancy** and **home region** — you'll need them for `oci` CLI config if you use it, though the Console-based steps below don't require it.

---

## 7. Provision a free OKE cluster

### 7.1 Quick Create (recommended — handles networking for you)

OCI Console → **Developer Services → Kubernetes Clusters (OKE) → Create Cluster → Quick Create**.

- **Name**: `practice-cluster`
- **Kubernetes API endpoint**: Public endpoint
- **Node type**: Managed
- **Cluster type**: **Basic** ← this is the free one; do not pick Enhanced ($0.10/hr)
- **Shape**: `VM.Standard.A1.Flex` (Ampere, Always Free eligible) — set OCPU/memory to fit your account's allowance from §6 (e.g. 2 OCPU/12GB on a single node if still on Free Tier account class, or up to 4/24 after the PAYG upgrade)
- **Node count**: 1 (you can add more later within your OCPU/memory budget)

Quick Create provisions the VCN, subnets, internet gateway, and security lists automatically — this is the detail that makes OKE meaningfully easier than hand-rolling AWS VPC networking for the EKS equivalent.

Click **Create Cluster**. This takes several minutes.

### 7.2 If you hit "Out of host capacity"

This is a real, commonly-reported Always Free friction point, not a sign anything is misconfigured:

- Retry — capacity frees up as other users' instances are released; this can take minutes to (rarely) hours.
- Try a different Availability Domain if your region has more than one (Placement section).
- The PAYG account upgrade from §6 gives standard scheduling priority and is the most reliable fix if this keeps happening.

### 7.3 Connect `kubectl`

Console → your cluster → **Access Cluster** → copy the `oci ce cluster create-kubeconfig ...` command (needs the `oci` CLI configured with an API key — the Console page walks you through generating one) or download the kubeconfig file directly from the same dialog. Either way:

```bash
kubectl get nodes
```

should show your Ampere node(s) `Ready`.

---

## 8. Push the image to GHCR (free)

GitHub Container Registry is free for public images and needs no separate account — just a [Personal Access Token](https://github.com/settings/tokens) with `write:packages` scope (or, if you wire this into Actions later per §4's note, the workflow's automatic `GITHUB_TOKEN` already has this scope, no PAT needed at all).

```bash
docker login ghcr.io -u <YOUR_GITHUB_USERNAME> -p <YOUR_PAT>
docker push ghcr.io/<YOUR_GITHUB_USERNAME>/micronaut-template:1.0.0
```

(If you used the `images = [...]` tag from §4.1, `./gradlew dockerPush` does the same thing.)

By default, a newly-pushed GHCR package is **private**, and a private package needs an `imagePullSecret` for OKE to pull it. Simplest path for a practice/demo deployment: on the package's GitHub page → **Package settings → Change visibility → Public** (it's a template project with no proprietary code in the image, so there's no real downside here). If you'd rather keep it private, see the troubleshooting table in §17 for the pull-secret alternative.

---

## 9. Namespace, ConfigMap, Secret

```bash
kubectl create namespace backend-practice
```

ConfigMap — non-secret values:

```bash
kubectl create configmap backend-config -n backend-practice \
  --from-literal=MICRONAUT_SERVER_HOST=0.0.0.0 \
  --from-literal=PG_HOST=postgres \
  --from-literal=PG_PORT=5432 \
  --from-literal=PG_DATABASE=thedb \
  --from-literal=APP_DOMAIN=https://app.yourname.duckdns.org \
  --from-literal=OIDC_ENABLED=false \
  --from-literal=COOKIE_SECURE=true \
  --from-literal=SESSION_ABSOLUTE_LIFETIME=12h \
  --from-literal=UNLEASH_API_URL=http://unleash:4242/api/ \
  --from-literal=UNLEASH_APP_NAME=micronaut-out-of-the-box-template
```

Secret — credentials (replace every value, never reuse local `.env` passwords in a place anyone else can reach):

```bash
kubectl create secret generic backend-secrets -n backend-practice \
  --from-literal=PG_USERNAME=postgres \
  --from-literal=PG_PASSWORD='<choose-a-strong-password>' \
  --from-literal=OIDC_CLIENT_ID=micronaut-app \
  --from-literal=OIDC_CLIENT_SECRET='<your-keycloak-client-secret>' \
  --from-literal=UNLEASH_API_TOKEN='*:development.client-token-123'
```

Why split these two: a ConfigMap's contents are plaintext in `kubectl get configmap -o yaml` and meant to be — they're not access-controlled. A Secret is base64-encoded at rest (not encrypted by default unless you've enabled [encryption at rest](https://kubernetes.io/docs/tasks/administer-cluster/encrypt-data/) on the cluster) and can be RBAC-restricted separately from ConfigMaps, so the convention is: credentials go in Secrets, everything else in ConfigMaps.

Leave `OIDC_ENABLED=false` for now — the app runs fine without Keycloak/Unleash (per the flag-gating behavior in `backend-tech-solution.md` §6.4/§7), you just won't be able to exercise login flows until §18.

---

## 10. Deploy Postgres in-cluster

Runs as a `StatefulSet` (stable network identity + a `PersistentVolumeClaim`, which a plain `Deployment` does not give you), backed by OCI's free 200GB block storage allotment (§5) — no separate database service to pay for or sign up to.

```yaml
# postgres.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: backend-practice
spec:
  serviceName: postgres
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
        - name: postgres
          image: postgres:17.5-bookworm
          ports:
            - containerPort: 5432
          env:
            - name: POSTGRES_DB
              valueFrom:
                configMapKeyRef: { name: backend-config, key: PG_DATABASE }
            - name: POSTGRES_USER
              valueFrom:
                secretKeyRef: { name: backend-secrets, key: PG_USERNAME }
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef: { name: backend-secrets, key: PG_PASSWORD }
          resources:
            requests: { cpu: 100m, memory: 256Mi }
            limits: { cpu: 500m, memory: 512Mi }
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data
              subPath: pgdata
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 5Gi
---
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: backend-practice
spec:
  clusterIP: None       # headless: StatefulSet pods get stable DNS, no load-balancing needed for a single replica
  selector:
    app: postgres
  ports:
    - port: 5432
```

(`subPath: pgdata` avoids a known Postgres-on-some-CSI-drivers issue with `lost+found` appearing at the volume root. `resources` requests are set explicitly and kept small — see §18 for why this matters on a 2–4 OCPU/12–24GB budget.)

```bash
kubectl apply -f postgres.yaml
kubectl get pods -n backend-practice -w   # wait for postgres-0 to be Running/1/1
```

Apply Liquibase changesets against it once it's up — easiest from your machine via a port-forward:

```bash
kubectl port-forward -n backend-practice svc/postgres 5433:5432 &
PG_HOST=localhost PG_PORT=5433 PG_DATABASE=thedb PG_USERNAME=postgres PG_PASSWORD=<...> ./gradlew :database:update
```

(This is the exact same `:database:update` task `.github/workflows/backend-ci.yml` runs against its own throwaway Postgres on every PR — see `backend-tech-solution.md` §3 for why migration ordering specifically is worth double-checking here.)

---

## 11. Deploy the backend

```yaml
# backend.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend
  namespace: backend-practice
spec:
  replicas: 2
  selector:
    matchLabels:
      app: backend
  template:
    metadata:
      labels:
        app: backend
    spec:
      containers:
        - name: backend
          image: ghcr.io/<YOUR_GITHUB_USERNAME>/micronaut-template:1.0.0
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef: { name: backend-config }
            - secretRef: { name: backend-secrets }
          readinessProbe:
            tcpSocket: { port: 8080 }
            initialDelaySeconds: 15
            periodSeconds: 10
          livenessProbe:
            tcpSocket: { port: 8080 }
            initialDelaySeconds: 30
            periodSeconds: 20
          resources:
            requests: { cpu: 250m, memory: 384Mi }
            limits: { cpu: "1", memory: 768Mi }
---
apiVersion: v1
kind: Service
metadata:
  name: backend
  namespace: backend-practice
spec:
  selector:
    app: backend
  ports:
    - port: 80
      targetPort: 8080
```

```bash
kubectl apply -f backend.yaml
kubectl get pods -n backend-practice -w
```

If a pod goes `CrashLoopBackOff`, check logs immediately — usually a missing/wrong env var (DB connection refused is the most common one at this stage):

```bash
kubectl logs -n backend-practice deploy/backend --previous
```

Two replicas exist here mainly to demonstrate that the Service load-balances across pods — on a tight free-tier compute budget (§18), one replica is fine too. This repo's auth model keeps one DB-backed session per user (`backend-tech-solution.md` §6.1), not per-pod sticky sessions, so multiple replicas are safe for login state regardless — sessions live in Postgres, not pod memory.

---

## 12. Install an Ingress controller (using OCI's free Load Balancer)

Kubernetes defines the `Ingress` API but ships no implementation — you install one. `ingress-nginx` is the most common choice and works identically on OKE:

```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
helm install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace \
  --set controller.service.annotations."oci\.oraclecloud\.com/load-balancer-type"=lb \
  --set controller.service.annotations."service\.beta\.kubernetes\.io/oci-load-balancer-shape"=flexible \
  --set controller.service.annotations."service\.beta\.kubernetes\.io/oci-load-balancer-shape-flex-min"="10" \
  --set controller.service.annotations."service\.beta\.kubernetes\.io/oci-load-balancer-shape-flex-max"="10"
```

The four annotations explicitly pin the provisioned Load Balancer to OCI's free Flexible shape at 10Mbps (§5) — without them, the OCI Cloud Controller Manager's defaults are generally fine too, but being explicit here removes any doubt about accidentally provisioning a bigger, billed shape.

```bash
kubectl get svc -n ingress-nginx ingress-nginx-controller
```

Wait for `EXTERNAL-IP` to populate (can take a minute or two). Note it down — you need it for the hostname next.

Now the `Ingress` resource itself, routing your hostname to the backend Service:

```yaml
# ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: backend
  namespace: backend-practice
  annotations:
    nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "3600"
spec:
  ingressClassName: nginx
  rules:
    - host: api.yourname.duckdns.org
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: backend
                port:
                  number: 80
```

The longer proxy timeouts matter because this app has **two** WebSocket endpoints — `/ws/persons` ([`PersonsWebSocket`](src/main/java/com/example/controller/PersonsWebSocket.java), idle ping/pong) and `/ws/insurance` ([`InsuranceWebSocket`](src/main/java/com/example/controller/InsuranceWebSocket.java), which replies to the client's periodic ping with a live snapshot of insurances expiring within 30 days). Both are plain HTTP paths under the same `path: /` rule, so no extra routing config is needed for the second one — but the same idle-timeout risk applies to both: ingress-nginx supports WebSocket upgrades automatically, but its default 60s proxy timeout would silently kill idle connections on either endpoint. (TLS is added to this same resource in §14 — apply it once now, you'll edit it again shortly.)

```bash
kubectl apply -f ingress.yaml
```

---

## 13. Get a real hostname for $0

Two options, neither requires buying anything:

### 13.1 You don't already own a domain: DuckDNS

[duckdns.org](https://www.duckdns.org/) — sign in (GitHub/Google/etc.), pick a subdomain (e.g. `yourname-api`), and point it at the `EXTERNAL-IP` from §12 directly in DuckDNS's dashboard. You get `yourname-api.duckdns.org` immediately, no propagation wait beyond DuckDNS's own (fast) update.

**Use DuckDNS for both frontend and backend if you go this route** — e.g. `yourname-api.duckdns.org` and `yourname-app.duckdns.org`. This matters for cookies: both are subdomains of the same registrable domain (`duckdns.org`), so the `SameSite=Strict` reasoning in §15 still holds exactly as if you owned the parent domain yourself. Mixing providers (DuckDNS for one, a different free DNS service for the other) would make them genuinely cross-site to the browser.

`nip.io`/`sslip.io` (`<ip-with-dashes>.sslip.io`, zero signup at all) are a viable zero-step alternative for quick testing, but DuckDNS is the better choice once you're wiring up real login flows, since the hostname stays stable if the LoadBalancer IP ever changes (just update the DuckDNS record), where an IP-embedded sslip.io hostname would not.

### 13.2 You already own a domain

Same as DuckDNS, just a normal DNS A record at your existing registrar/DNS provider:

```
Type: A
Name: api   (i.e. the record for api.yourdomain.com)
Value: <EXTERNAL-IP from §12>
TTL: 300 (or default)
```

Either way, verify with:

```bash
nslookup api.yourname.duckdns.org
# or
dig +short api.yourname.duckdns.org
```

Once that returns your LoadBalancer's IP, `curl -H "Host: api.yourname.duckdns.org" http://<EXTERNAL-IP>/persons` should already work over plain HTTP — confirming Ingress routing before adding TLS into the mix (so you only ever debug one new variable at a time).

---

## 14. HTTPS with cert-manager + Let's Encrypt

Free regardless of where the hostname came from — Let's Encrypt's HTTP-01 challenge only cares that the hostname resolves to your Ingress, not who issued it.

```bash
helm repo add jetstack https://charts.jetstack.io
helm repo update
helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace \
  --set crds.enabled=true
```

Create a `ClusterIssuer`:

```yaml
# cluster-issuer.yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: you@example.com
    privateKeySecretRef:
      name: letsencrypt-prod-key
    solvers:
      - http01:
          ingress:
            ingressClassName: nginx
```

```bash
kubectl apply -f cluster-issuer.yaml
```

Now point the Ingress at it and request a cert (the "edit it again" from §12):

```yaml
# ingress.yaml (updated)
metadata:
  name: backend
  namespace: backend-practice
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
    nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "3600"
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - api.yourname.duckdns.org
      secretName: backend-tls
  rules:
    - host: api.yourname.duckdns.org
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: backend
                port:
                  number: 80
```

```bash
kubectl apply -f ingress.yaml
kubectl get certificate -n backend-practice -w   # wait for READY: True
```

DNS **must** already be resolving correctly (§13) before this works. `kubectl describe certificate -n backend-practice backend-tls` and `kubectl describe challenge -n backend-practice` are the tools for diagnosing a stuck issuance.

Once `READY: True`, `https://api.yourname.duckdns.org/persons` should work with a real, trusted cert.

---

## 15. Production config: CORS, cookies, redirects

**`APP_DOMAIN=https://app.yourname.duckdns.org`** — this single value drives both `micronaut.server.cors.configurations.web.allowed-origins` *and* the OIDC `login-success`/`logout` redirect targets ([`application.yml:13,54,67-68`](src/main/resources/application.yml#L13)). CORS in this app is intentionally a single explicit origin, not a wildcard — `allow-credentials: true` is set, and per the CORS spec, `Access-Control-Allow-Origin: *` is disallowed together with credentialed requests anyway, so wildcarding isn't even an option here.

**`COOKIE_SECURE=true`** — the `authToken` cookie ([`application.yml:74`](src/main/resources/application.yml#L74)) gets the `Secure` attribute, meaning the browser will refuse to send it over plain HTTP. Leave this `false` locally (HTTP), set `true` once you're behind real TLS — forgetting this is a common "login works locally, silently fails in prod" bug.

**Cross-(sub)domain cookies and `SameSite=Strict`** — the cookie is configured `cookie-same-site: Strict` ([`application.yml:73`](src/main/resources/application.yml#L73)). This is *not* actually a problem for `app.yourname.duckdns.org` calling `api.yourname.duckdns.org`, even though they're different hosts: `SameSite` is evaluated against the **registrable domain** (`duckdns.org`, the eTLD+1), not the exact hostname — exactly the same reasoning as two subdomains of a domain you own yourself, see §13.1. This only breaks if frontend and backend end up on genuinely different registrable domains (e.g. backend on `duckdns.org` but frontend on `vercel.app`) — in that case you'd need `SameSite=None; Secure` instead, with the security trade-off that implies.

**Keycloak redirect URIs** — if you wire up §18, the Keycloak client (`keycloak/micronaut-realm.json`) needs `https://api.yourname.duckdns.org/oauth/callback/oidc` added to its valid redirect URIs, and the client's "Web origins" needs `https://app.yourname.duckdns.org`. Forgetting this produces Keycloak's generic "Invalid redirect uri" error page, not an app-side error — worth knowing so you don't go looking in the wrong logs.

---

## 16. Wire up the frontend

Whatever the frontend is (and wherever it's hosted — Netlify/Vercel/Cloudflare Pages all have their own genuinely-free static-hosting tiers, or you could run it in this same free OKE cluster as a second Deployment+Ingress on a second hostname), it needs two things:

1. Base API URL set to `https://api.yourname.duckdns.org` (build-time env var, however your frontend framework does it).
2. Every request that needs auth must send credentials, since auth here is a cookie, not a bearer token:
   - `fetch(url, { credentials: 'include' })`
   - or, for axios: `axios.defaults.withCredentials = true`

Without `credentials: 'include'`/`withCredentials: true`, the browser won't attach the `authToken` cookie to cross-origin requests even though CORS is configured correctly server-side — these are two independent, both-required mechanisms (CORS controls whether the *response* is readable; the credentials flag controls whether the cookie is *sent* at all).

---

## 17. Verify end-to-end + troubleshooting

```bash
curl -i https://api.yourname.duckdns.org/persons
```

From the actual frontend running in a browser, open devtools → Network tab, confirm the request to `api.yourname.duckdns.org` shows `Set-Cookie`/`Cookie` headers as expected and no CORS error in the console.

| Symptom | Likely cause |
|---|---|
| Stuck "Out of host capacity" creating the node pool | §7.2 — retry, try another AD, or upgrade to PAYG account class (§6) |
| Pod `Running` but `READY 0/1` forever | `MICRONAUT_SERVER_HOST` not set to `0.0.0.0` (§2.1) |
| `CrashLoopBackOff` | Check `kubectl logs --previous`; almost always a DB connection failure — wrong `PG_HOST`/credentials |
| `ImagePullBackOff` | GHCR package is still private and no pull secret configured (§8) — either make it public, or `kubectl create secret docker-registry ghcr-pull --docker-server=ghcr.io --docker-username=<user> --docker-password=<PAT> -n backend-practice` and add `imagePullSecrets: [{name: ghcr-pull}]` to the Deployment's pod spec |
| Ingress returns nginx default 404 | `Host` header doesn't match, or DNS hasn't propagated yet — test with `curl -H "Host: api.yourname.duckdns.org" http://<EXTERNAL-IP>/...` to bypass DNS |
| `Certificate` stuck `READY: False` | DNS not pointing at the cluster yet when the HTTP-01 challenge ran — fix DNS, then `kubectl delete order,challenge -n backend-practice --all` to force a retry |
| Browser console: CORS error | `APP_DOMAIN` doesn't exactly match the frontend's origin (scheme + host + port all matter) |
| Login redirect ends in Keycloak error page | Redirect URI not registered for the client in Keycloak (§15) |
| WebSocket disconnects after ~60s idle | Missing the `proxy-read-timeout`/`proxy-send-timeout` ingress annotations (§12) |
| Logged in fine, but every write (create/update/delete) 403s for every user, including admins | `GET /user`'s `roleGroups` is empty — Keycloak client roles/`roles` mapper weren't seeded on this realm (§18) |
| A flag-gated feature (e.g. the whole Persons list) just shows nothing, no error | The Unleash flag doesn't exist on this instance yet — flags don't ship as code (§18) |

---

## 18. Optional: Keycloak & Unleash in-cluster

For full parity with the local `docker-compose.yaml` (OIDC login and feature flags actually working in this deployed environment), both need to run somewhere reachable. This is meaningfully more setup than the backend itself, and meaningfully tighter on a 2 OCPU/12GB (or even 4/24) free budget once you add Keycloak's and Unleash's own Postgres instances — treat it as a stretch goal once §1–17 are working, and read the resource note below before deciding whether to attempt it on the same cluster.

- **Keycloak**: the [Bitnami Helm chart](https://github.com/bitnami/charts/tree/main/bitnami/keycloak) or the official [Keycloak Operator](https://www.keycloak.org/operator/installation) both work; either way it needs its own Postgres (a second StatefulSet, or — to conserve resources, see below — a second database on the *same* Postgres instance from §10), its own hostname (e.g. `auth.yourname.duckdns.org`), and — since TLS now terminates at the ingress, not at Keycloak itself — `KC_PROXY=edge`, `KC_HOSTNAME=auth.yourname.duckdns.org`, `KC_HOSTNAME_PROTOCOL=https` (mirroring the `KC_PROXY`/`KC_HOSTNAME*` vars already in `docker-compose.yaml`).
- **Unleash**: has an [official Helm chart](https://github.com/Unleash/unleash-helm-charts); same pattern — its own Postgres (or another database on the shared instance), optionally its own hostname if you want the admin UI reachable, otherwise it only needs to be reachable *inside* the cluster (`UNLEASH_API_URL=http://unleash:4242/api/`, no Ingress needed at all for that case).

**Resource budget tip**: rather than three separate Postgres StatefulSets (app + Keycloak + Unleash, mirroring local `docker-compose.yaml`'s three separate containers), point all three at the *same* in-cluster Postgres from §10 using three separate databases (`thedb`, `keycloak`, `unleash`) on one instance. Saves real memory headroom on a free-tier node, at the cost of losing the local setup's "each service has its own DB instance" isolation — a reasonable trade for a free practice deployment, not necessarily one you'd make in a paid production setup.

**Two seeding steps people forget, because neither ships as code — both are genuinely empty on a brand-new instance:**

1. **Unleash flags don't exist until you create them.** `get-persons`, `persons-table`, `oidc-auth`, `smart-id-auth` all need to be created (Admin UI or API) and explicitly enabled for whichever environment the app's API token is scoped to. A missing flag isn't an error — `isEnabled()` just silently returns `false`, so the symptom is "this feature does nothing" rather than a crash. `persons-table` in particular gates the entire Persons list UI; missing it looks exactly like an empty database.
2. **Keycloak's realm import only fires on a genuinely new realm.** `--import-realm` skips importing into a realm name that already exists, so if you're pointing at a pre-existing Keycloak rather than a freshly-created one, `keycloak/micronaut-realm.json`'s client roles (`example.admin`, `example.head-user`, `example.end-user`) and the `roles` protocol mapper's `usermodel.clientId` config (see `backend-tech-solution.md` §6.6) need to be applied by hand via the Admin Console/API instead. Without them, login works fine but every authenticated user resolves to **zero role groups** — read endpoints work, every `@RequiresRoleGroup`-protected write 403s for everyone, including admins.

Once both are up, set `OIDC_ENABLED=true` and the real `OIDC_ISSUER_URL`/`OIDC_CLIENT_SECRET` in the backend's ConfigMap/Secret, and update the Keycloak client's redirect URIs as described in §15.

---

## 19. Staying within Always Free (no teardown required)

Unlike a paid cluster, there's no hourly meter running here — nothing in this guide bills you for existing, only for exceeding the Always Free limits (§5/§6), which nothing here does by default. So there's no cost-driven urgency to delete anything.

Two things worth knowing instead:

- **OCI can reclaim idle Always Free resources** if the *account* (not just one resource) goes unused for an extended period — log in occasionally if you're not actively building on it, the same way you'd keep any free account "alive."
- If you genuinely want to tear it down (cleaning up before trying a different region, for instance):

```bash
kubectl delete -f ingress.yaml
helm uninstall ingress-nginx -n ingress-nginx   # releases the Load Balancer
# then delete the cluster + node pool from the OCI Console, or:
oci ce cluster delete --cluster-id <cluster-ocid>
```

All your YAML manifests are reusable — recreating the cluster and re-`kubectl apply -f`-ing everything gets you back to the same state in a few minutes (Postgres data does not survive cluster deletion, since the PVC's underlying block volume is also deleted — back up with `pg_dump` first if it matters).

---

## 20. Checklist summary

- [ ] §2: env-var overrides identified (`MICRONAUT_SERVER_HOST=0.0.0.0` especially)
- [ ] §4: image builds and runs locally with `docker run`, responds on `/persons`
- [ ] §6: OCI account created, (recommended) upgraded to Pay-As-You-Go account class while staying within Always Free limits
- [ ] §7: OKE **Basic** cluster created on Ampere A1 nodes, `kubectl get nodes` works
- [ ] §8: image pushed to GHCR, pullable by the cluster (public, or pull secret configured)
- [ ] §9: namespace + ConfigMap + Secret applied
- [ ] §10: Postgres `StatefulSet` running, Liquibase changesets applied
- [ ] §11: backend `Deployment` pods `Running`/`Ready`
- [ ] §12: ingress-nginx installed (LB pinned to the free Flexible 10Mbps shape), `Ingress` applied, plain-HTTP `curl` by IP works
- [ ] §13: free hostname (DuckDNS or your own domain) created and resolving
- [ ] §14: cert-manager installed, `Certificate` `READY: True`, HTTPS works
- [ ] §15: `APP_DOMAIN`/`COOKIE_SECURE` set for production
- [ ] §16: frontend points at the new URL with credentialed requests
- [ ] §17: verified end-to-end in a real browser
- [ ] §18 (if doing OIDC/flags in this environment): Unleash flags created+enabled, Keycloak client roles + `roles` mapper's `usermodel.clientId` set — test login as each of `adminuser`/`headuser`/`testuser` and confirm `GET /user`'s `roleGroups` is non-empty before assuming anything role-gated is broken
- [ ] §19: confirmed nothing provisioned falls outside Always Free limits (cluster type Basic, node shape A1.Flex within your OCPU/memory allowance, LB shape Flexible/10Mbps, total block storage under 200GB)

---

## 21. Resources & References

**§1 — Core concepts**
- Kubernetes docs: [Deployments](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/), [Services](https://kubernetes.io/docs/concepts/services-networking/service/), [Ingress](https://kubernetes.io/docs/concepts/services-networking/ingress/), [StatefulSets](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/), [PersistentVolumes](https://kubernetes.io/docs/concepts/storage/persistent-volumes/)

**§2 — Micronaut-specific behavior**
- [Micronaut User Guide](https://docs.micronaut.io/latest/guide/) — environment variable property source precedence, CORS, security cookie config

**§3 — Tooling**
- [kubectl install](https://kubernetes.io/docs/tasks/tools/), [Helm install](https://helm.sh/docs/intro/install/), [OCI CLI install](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/cliinstall.htm)

**§4 — Containerizing**
- [Micronaut Gradle Plugin docs](https://micronaut-projects.github.io/micronaut-gradle-plugin/latest/) (Docker/`dockerBuild` tasks)
- [Docker multi-stage builds](https://docs.docker.com/build/building/multi-stage/)

**§5–7 — OCI / OKE free tier**
- [Oracle Cloud Free Tier](https://www.oracle.com/cloud/free/) | [Always Free Resources reference](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)
- [Comparing Enhanced and Basic OKE clusters](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contengcomparingenhancedwithbasicclusters_topic.htm) (confirms Basic = free control plane, Enhanced = $0.10/hr)
- [OKE pricing](https://www.oracle.com/cloud/cloud-native/kubernetes-engine/pricing/)
- Community write-ups on the 2026 Ampere allowance change and capacity workarounds: [TerminalBytes](https://terminalbytes.com/oracle-cloud-free-tier-changes-2026/), [Calvin Bui — Kubernetes on OCI Free Tier](https://calvin.me/kubernetes-on-oracle-cloud-free-tier/), [hitrov/oci-arm-host-capacity](https://github.com/hitrov/oci-arm-host-capacity)

**§8 — Container registry**
- [GitHub Container Registry docs](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)

**§9 — Config & Secrets**
- Kubernetes docs: [ConfigMaps](https://kubernetes.io/docs/concepts/configuration/configmap/), [Secrets](https://kubernetes.io/docs/concepts/configuration/secret/)

**§11 — Probes**
- Kubernetes docs: [Liveness/Readiness/Startup probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)

**§12 — Ingress controller / OCI Load Balancer annotations**
- [ingress-nginx install/deploy docs](https://kubernetes.github.io/ingress-nginx/deploy/)
- [OCI Cloud Controller Manager load balancer annotations](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contengcreatingloadbalancer.htm)

**§13 — Free hostnames**
- [Duck DNS](https://www.duckdns.org/) | [sslip.io](https://sslip.io/) / [nip.io](https://nip.io/)

**§14 — TLS**
- [cert-manager installation](https://cert-manager.io/docs/installation/), [cert-manager ACME/HTTP-01 configuration](https://cert-manager.io/docs/configuration/acme/), [Let's Encrypt docs](https://letsencrypt.org/docs/)

**§15 — CORS / cookies**
- MDN: [CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS), [`Set-Cookie` `SameSite`](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie/SameSite)

**§18 — Keycloak / Unleash Helm charts**
- [Bitnami Keycloak chart](https://github.com/bitnami/charts/tree/main/bitnami/keycloak), [Keycloak Operator](https://www.keycloak.org/operator/installation), [Unleash Helm charts](https://github.com/Unleash/unleash-helm-charts)
