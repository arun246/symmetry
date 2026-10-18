# AGENTS.md

## Repo state
- **Design-only.** Markdown docs only — no code, no build/test/lint tooling.
- It **is** a git repo now (branch `main`, remote `origin` → GitHub `arun246/symmetry`).
- Do **not** invent or run build/test commands — none exist yet. Add tooling before claiming anything is verified.

## Source of truth (priority order)
1. `README.md` — overview
2. `docs/DESIGN.md` — full design
3. `docs/STACK.md` — tech stack (services, libraries, DB, AI plan) ← **agreed**
4. `docs/NEW-BUILDS.md` — the `build` track (new apps: proposals, rates, milestones, quality bar)
5. `docs/IMPLEMENTATION.md` — P1–P2 plan
6. `docs/FUTURE-PROOFING.md` — design space & the seven sockets (= seams)

Decisions are repeated across these docs. When you change one, reconcile the others.

## Host toolchain
- Available: **Java 21** (`javac`), **Node 20 + npm**, **Python 3.13 + pip**, **Docker** (installed; the daemon may be off), **curl/unzip**.
- Absent: **Maven, Gradle, Rust/cargo**, and **no GPU**. Any Java build needs a committed wrapper (`./mvnw`) or Docker; do not assume `mvn` or `cargo` exist. Spring projects can be generated via `https://start.spring.io` (reachable).

## Load-bearing decisions (easy to get wrong)
- **Stack (see `docs/STACK.md`):** Java 21 + Spring Boot REST API (`platform/`), React + **AWS Cloudscape** UI (`ui/`), Python FastAPI AI service (`ai/`), Rust→WASM modules (`wasm-sdk/`), **PostgreSQL** (not MySQL), MinIO + Redis.
- Never use **H2**; tests use **Testcontainers Postgres**.
- Language is **not fixed to Java/Rust**. For `optimize`, the task committee sets it from the existing software (any language); for `build`, the worker proposes it. Java and Rust are just the first two **toolchain-registry** entries.
- Two `task_kind`s: **`optimize`** (improve an existing module) and **`build`** (brand-new app: design proposal + hourly/fixed rate + milestones). See `docs/NEW-BUILDS.md`; V1 ships only `optimize`.
- **Java evaluation = JVM-tier sandbox** (isolated process/container). Java→WASM (TeaVM) is a *discouraged narrow exception*, not the default — it drops the JIT and measures the wrong target.
- **Rust evaluation = `wasm32` in Chicory** (no host imports, fuel/memory metered).
- **Correctness is a gate**; resource metrics only rank submissions that already pass.
- V1 ships **one workload cell (compute kernels)**, but core interfaces must stay cell-agnostic. Install the **seven sockets** in `docs/FUTURE-PROOFING.md` before writing code; do not hardcode the V1 cell.
- A submission is ineligible without a **signed acceptance of the task document** and the M1b agreements.

## Build gotchas (easy to get wrong)
- **Spring Boot 4, not 3.** `platform/` is Spring Boot 4.1.1 → Spring Framework 7
  → **Jackson 3**, whose packages are `tools.jackson.*` (annotations stay
  `com.fasterxml.jackson.annotation`). Do not import `com.fasterxml.jackson.databind.*`.
- **Security is off by default** for local dev (`symmetry.security.enabled=false`).
  Set it true plus an OAuth2 issuer to require a JWT.
- **Verify commands:**
  - `platform/`: `./mvnw -B verify` (the Testcontainers test needs Docker; it is
    skipped automatically when Docker is absent, so pure unit tests still run).
  - `ui/`: `npm ci && npm run build`.
  - `ai/`: `python -c "import app.main"`.
  - `wasm-sdk/`: `cargo build --release --target wasm32-unknown-unknown` (needs Rust).

## Working protocol
- **Two-way doors** (reversible): proceed, report after.
- **One-way doors** (irreversible/consequential): stop and ask first — one question at a time, with a recommendation. **"NO" is a valid answer.**
- If running a command could answer the question, run it instead of asking.
- Explain in plain language; the user is a novice programmer — avoid unexplained jargon.

> "Once I open a one-way door, it stays open for that work, including for any subagents you hand it to." — user
