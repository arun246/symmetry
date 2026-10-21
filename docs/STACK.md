# Symmetry: Future of software tech work — Technology Stack

This document records **what we build with**. It is the agreed stack for the
Minimum Viable Product (MVP) — the smallest first version. It is plain-language:
every tool is explained the first time.

If you only remember one line: **Java Spring Boot API + Rust→WASM modules +
Python AI service + PostgreSQL + React with a custom macOS-style UI.**

---

## 1. The shape of the system (four services + two stores)

| # | Piece | Built with | What it does |
|---|---|---|---|
| 1 | **API (control plane)** | **Java 21 + Spring Boot 4** (REST) | The system of record and coordinator: contracts, task documents, acceptance, submissions, builds, evaluation, scoring, trust, escrow. |
| 2 | **UI console** | **React + custom Harbor design system** | The website. macOS-Settings-inspired, solid surfaces, light/dark. |
| 3 | **AI service** | **Python** (FastAPI) | Two assistants: help organizations *write tasks*, and help workers *write submissions*. |
| 4 | **Worker module SDK** | **Rust → WebAssembly (WASM)** | The shape a submitted module must take, plus example modules and a local test kit. |
| 5 | **System of record** | **PostgreSQL 16+** | The database. Holds all records. |
| 6 | **Artifacts + queue/cache** | **MinIO/S3 + Redis** | Files (submissions, `.wasm`, logs, SBOMs) and fast scratch space (jobs, limits, sessions). |

"Control plane" just means the part that coordinates work rather than doing the
heavy computing itself.

---

## 2. Why these choices

- **Java + Spring Boot** for the API: the design already chose it, it is a mature
  way to build REST services, and it matches the `oct/java/` workspace.
- **Rust → WASM** for modules: WASM with *no outside connections* is safe to run
  by construction, tiny, and precisely measureable. Rust produces very small,
  fast, predictable WASM.
- **Python + FastAPI** for AI: the AI ecosystem (model gateways, MCP, redaction
  tools) is most mature in Python.
- **PostgreSQL** (see section 5) is the best fit, not MySQL.
- **React + a custom design system** so the console matches the Harbor/macOS
  look exactly; **Radix UI** supplies the accessible primitives.

---

## 3. Repository layout

```
symmetry/
  docs/                    # these design documents
  platform/                # Java 21 + Spring Boot REST API + evaluator
  ui/                      # React + custom Harbor design system
  ai/                      # Python FastAPI AI service
  wasm-sdk/                # Rust module SDK + example modules
  contracts/               # published contract files (.json)
  tasks/                   # task documents (text + machine-readable checklist)
  examples/                # practice modules (e.g. image-resize)
  deploy/                  # docker-compose and local infra
  .github/workflows/       # CI (build, test)
```

---

## 4. Libraries per service

### 4.1 Java API — `platform/`

| Concern | Library | Why (plain) |
|---|---|---|
| Web/API | **Spring Boot 4** (WebMVC, Validation, Actuator, Security) | Builds the REST endpoints, health checks, and (optionally) JWT login. |
| Persistence | **Spring Data JPA** + **jOOQ** | JPA for everyday save/load; jOOQ for complex reporting/leaderboard queries. |
| Migrations | **Flyway** | Versioned, repeatable database changes owned by Java. |
| DB driver | **PostgreSQL JDBC** | Talks to Postgres. |
| WASM sandbox | **Chicory** (`runtime`, `wasm`, `wasi`) | Runs untrusted WASM inside the Java program, with fuel and memory limits. |
| JSON + validation | **Jackson** + a **contract document validator** | Reads contracts; checks the required sections, that limits are positive, and that invariants are not empty. (Spring Boot 4 ships **Jackson 3**, whose packages are `tools.jackson.*` — not `com.fasterxml.jackson.*`. A networknt JSON Schema pass is planned.) |
| Security | **Spring Security OAuth2 Resource Server** + **Keycloak** | Login and permissions; one open-source identity server for everyone. The resource server is wired but **off by default** for local dev (`symmetry.security.enabled=false`); turn it on with a Keycloak issuer to require a JWT. |
| Rate limiting | **Bucket4j** | Stops abuse per company/worker. |
| Cache | **Caffeine** | Fast repeated reads (hot contracts). |
| Scheduling/locks | **ShedLock** + Spring scheduler | Run background jobs once, not twice. |
| Metrics/tracing | **Micrometer** + Prometheus + OpenTelemetry | Numbers for dashboards and debugging. |
| Signing | JDK 21 **Ed25519** (or BouncyCastle) | Sign verdicts/artifacts. |
| Tests | **JUnit 5, AssertJ, Mockito, Testcontainers, jqwik, WireMock** | Unit, integration, property, and fake-API tests. |
| API docs | **springdoc-openapi** | Auto-generated Swagger UI. |
| Supply chain | **CycloneDX** plugin, **Semgrep**, **Trivy/Grype**, **Syft** | SBOM, static security scan, dependency and license scan for the quality gate. |

### 4.2 UI console — `ui/`

Design decision: the console is a **custom macOS-Settings-inspired design system**
(Harbor palette, light/dark, solid surfaces). Cloudscape was dropped because its
AWS visual language cannot be themed into this design; see `docs/UI-PLAN.md`.

| Concern | Library | Why (plain) |
|---|---|---|
| Design system | **Custom** CSS tokens + components (`ui/src/ui/`) | Exact Harbor look; no fighting a vendor theme. |
| Accessible primitives | **Radix UI** (dialog, popover, select, tabs, switch, toast, tooltip) | Headless behaviour and keyboard/focus handling we would otherwise get wrong. |
| Charts | **Recharts** (custom tokens) | Donut/bar/line with the quiet macOS look; paired with a table fallback. |
| Code view | **CodeMirror 6** | Show submission code without Cloudscape/Monaco. |
| AI chat panel | **Custom** panel over the `ai/` API | Matches the shell; no vendor chat chrome. |
| Data fetching | **TanStack Query** | Talks to the Java REST API and caches results. |
| Auth | **`oidc-client-ts`** + **Keycloak** | Authorization Code + PKCE; admin is a realm role. |
| Logs | **xterm.js** | A terminal-style view of build logs. |

### 4.3 AI service — `ai/`

| Concern | Library | Why (plain) |
|---|---|---|
| Web | **FastAPI** + **Uvicorn** | Small, fast Python API. |
| Model calls | **OpenAI Python SDK** pointing at an **OpenAI-compatible gateway** | One interface; swap the model without changing code. |
| Gateway | **LiteLLM** | Routes to hosted DeepSeek now or self-hosted vLLM later. LiteLLM is also where **per-company rate limits and cost accounting** live (section 6), so the AI service itself stays thin. |
| Data shapes | **pydantic** | Validate requests/responses. |
| Agents | **`mcp` (Model Context Protocol) SDK** | Let coding agents safely use Symmetry tools. |
| Redaction | **presidio** (optional) | Strip private data before sending prompts. |
| Retrieval | **pgvector** (optional) | Keep embeddings in the same Postgres. |

### 4.4 Rust module SDK — `wasm-sdk/`

| Concern | Crate/tool | Why (plain) |
|---|---|---|
| Target | `wasm32-unknown-unknown` (import-free) | No outside connections = safe by construction. |
| ABI | `#[no_mangle] extern "C"` `alloc`/`dealloc`/`run` + a `symmetry-sdk` crate | One simple interface every module follows. |
| Data | raw **length-prefixed bytes** in V1 (compact and dependency-free) | A simple, predictable wire shape. `serde` + `postcard` are added when structured messages are needed. |
| Errors | `thiserror` | Clean error values. |
| Tests | `proptest`, `arbitrary`, `cargo-fuzz`, `wasmtime` | Same property/fuzz testing idea, locally. |
| Size | `wasm-opt` (binaryen), `twiggy`, `wasm-tools` | Meet the module size budget. |
| Determinism | avoid `rand`/`getrandom`; use `BTreeMap` | Pass the "same input, same output" check. |

---

## 5. Database decision: PostgreSQL (not MySQL)

**Decision: PostgreSQL 16+ as the single system of record.** Reasons:

1. **JSONB** stores contract documents and flexible metric lists, indexable and
   queryable — stronger than MySQL's JSON.
2. **Integrity for money.** Escrow and bounties need strong constraints; Postgres
   has the richest set (`CHECK`, foreign keys, exclusion constraints).
3. **Row-Level Security** for keeping different companies' data apart (multi-tenant).
4. **Extensions**: `pgcrypto` (hashing), `pg_trgm` (search), `uuid`, and later
   `Timescale` for metrics, `pgvector` for AI.
5. **Replication/CDC** (Debezium) for scaling and the append-only audit ledger.
6. It is the AWS-native managed option (**Aurora PostgreSQL**), matching the
   AWS-style product.

MySQL/MariaDB would work but offers no advantage here and fewer of the above.

**Supporting stores (not replacements):**

- **Object storage:** MinIO locally, S3 in production — submissions, `.wasm`,
  logs, SBOMs (never large blobs in the database).
- **Redis (Valkey):** job queue for evaluations, rate limits, sessions.
- Later: **OpenSearch** for search; **Prometheus/Grafana** for metrics.

**Dev/test note:** do **not** use H2. It behaves differently from Postgres and
gives false confidence. Use **Testcontainers** to run a real Postgres in tests.

---

## 6. AI plan (hosted model + agents)

Two features, one gateway:

1. **Organization assistant** — helps a company *write the task*: requirements,
   acceptance criteria, property tests, task documents, and disclosure hints. It
   may see the company's private context; its output is a **draft the company
   approves**.
2. **Worker copilot** — helps a worker *write a submission* against the **public**
   task document and sample data. It must **never** see the hidden tests/holdout.

**Architecture:** `ui`/`platform` → **AI service** → **LiteLLM gateway** → model.

**Hosting:** start with a **hosted DeepSeek** endpoint (open-weights, MIT license)
because this machine has **no GPU**. Self-host later with **vLLM** once GPUs exist
(DeepSeek-Coder-V2-Lite ~16B needs ~2 GPUs / ~32GB VRAM; the largest variants need
multi-GPU A100/H100 nodes).

**Guardrails (must-haves):**

- AI use must be **declared** (the M1b agreements) and **audit-logged**.
- **Redact** secrets and hidden data before any prompt is sent.
- **Per-company isolation**; no cross-tenant context.
- **Rate limits and cost accounting** per company.
- Platform tools are exposed over **MCP** so coding agents operate inside the same
  limits.

---

## 7. First build step (walking skeleton) — status

Built so far (details and "done when" tests in `docs/IMPLEMENTATION.md`):

1. `deploy/docker-compose.yml`: Postgres, MinIO, Redis, Keycloak (image versions
   pinned; healthchecks on the ones that support them).
2. `platform/`: Maven **wrapper** + Spring Boot skeleton + Flyway
   `V1__baseline.sql` (a single, hardened baseline: `contract` + `acceptance`,
   with named constraints, checks, indexes, and an `updated_at` trigger).
3. Contract records + validation (the language must be in the **toolchain
   registry**); REST: `POST/GET /contracts`,
   `GET /contracts/{id}/{version}`, `GET /contracts/{id}/{version}/workbench`
   (tier-filtered), and `POST /contracts/{id}/accept` (Ed25519-verified and
   agreement-gated).
4. `ui/`: React + custom shell (Harbor tokens) + a Contracts table calling the API.
5. `ai/`: FastAPI stub with `/health` and one gateway passthrough (audit-logged).
6. `wasm-sdk/`: the ABI crate + one `image-resize` example.
7. CI: `./mvnw verify` (Testcontainers Postgres) + `npm run build` + `cargo build`.

Core interfaces are cell-agnostic (work kind, lifecycle, toolchain registry,
keycard, checker list, flexible metrics); V1 fills only the compute cell. Next:
deepen M1b → M2 → M3, then the `build` track.

---

## 8. Open decisions

- UI build tool: **decided — Vite single-page app** (see `ui/README.md`).
- When to self-host the model (vLLM) rather than use the hosted endpoint.
- Whether to keep jOOQ alongside JPA from day one, or add it only when reporting
  queries appear.
- Whether the full M1b **disclosure assessor** runs inside the platform or as a
  service the company owns.
