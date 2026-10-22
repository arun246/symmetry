# AGENTS.md

## Repo state
- **Has code now:** docs in `docs/` (including `docs/adr/`), plus `platform/` (Spring Boot), `ui/` (React), `ai/` (FastAPI), `wasm-sdk/` (Rust), and `deploy/`.
- **Planned:** `runner/` (AlmaLinux appliance + KVM microVM launcher + guest images). No runner code exists yet.
- It **is** a git repo (branch `main`, remote `origin` → GitHub `arun246/symmetry`).
- Use only the verify commands below; do not invent build/test commands. Add tooling before claiming anything is verified.

## Source of truth (priority order)
1. `README.md` — overview
2. `docs/DESIGN.md` — full design
3. `docs/adr/` — accepted runner, licensing, AI and update decisions
4. `docs/STACK.md` — tech stack (services, runner, libraries, DB, AI plan) ← **agreed**
5. `docs/NEW-BUILDS.md` — the `build` track (new apps: proposals, rates, milestones, quality bar)
6. `docs/IMPLEMENTATION.md` — P1–P2 plan

Decisions are repeated across these docs. When you change one, reconcile the others.

## Host toolchain
- Available: **Java 21** (`javac`), **Node 20 + npm**, **Python 3.13 + pip**, **Docker** (installed; the daemon may be off), **curl/unzip**.
- Absent: **Maven, Gradle, Rust/cargo**, and **no GPU**. Any Java build needs a committed wrapper (`./mvnw`) or Docker; do not assume `mvn` or `cargo` exist. Spring projects can be generated via `https://start.spring.io` (reachable).

## Load-bearing decisions (easy to get wrong)
- **Stack (see `docs/STACK.md`):** Java 21 + Spring Boot REST API (`platform/`), React + **custom Harbor design system** UI (`ui/`), Python FastAPI AI service (`ai/`), **AlmaLinux + KVM runner appliance** (`runner/`, planned), Rust→WASM modules (`wasm-sdk/`), **PostgreSQL** (not MySQL), MinIO + Redis.
- Never use **H2**; tests use **Testcontainers Postgres**.
- Language is **not fixed to Java, Rust or Python**. For `optimize`, the task committee sets it from the existing software (any language); for `build`, the worker proposes it. Java, Rust and Python are the first three **toolchain-registry** entries.
- Two `task_kind`s: **`optimize`** (improve an existing module) and **`build`** (brand-new app: design proposal + hourly/fixed rate + milestones). See `docs/NEW-BUILDS.md`; V1 ships only `optimize`.
- **Runner isolation = KVM microVM** (Firecracker preferred; Kata on the neutral Kubernetes fleet). The publisher owns hardware/electricity; Symmetry owns software, updates and support. **Never run untrusted code in a plain container or chroot.**
- **Java evaluation = JVM tier inside the microVM.** Java→WASM (TeaVM) is a *discouraged narrow exception*, not the default — it drops the JIT and measures the wrong target.
- **Rust evaluation = `wasm32` in Chicory inside the microVM** (no host imports, fuel/memory metered). **Python = CPython 3.13 inside the microVM** with pinned offline wheels.
- **Host OS = AlmaLinux 9; guests = Alpine** where possible, glibc guests for Java/Python where musl breaks. Images: qcow2/OVA (Tier 1), AWS AMI + GCP image (Tier 2, only nested-virt instance types). No RHEL qcow2 redistribution.
- **Correctness is a gate**; resource metrics only rank submissions that already pass. Results are signed by the runner and verified by the control plane.
- **AI payment is final on the first decision**, with escrow, one evidence-based human appeal, a pinned model version and a liability cap. See `docs/adr/0003-ai-adjudication.md`.
- **Updates are signed** (TUF-style, anti-rollback) and available online plus as offline bundles. Open-source code does not authenticate a binary.
- **Licensing is split:** Apache-2.0 for `runner/` + `wasm-sdk/`; AGPL-3.0 for `platform/` + `ai/` + `ui/` + `deploy/`; commercial support/updates. See `LICENSING.md`.
- V1 ships **one workload cell (compute kernels)**, but core interfaces must stay cell-agnostic; do not hardcode the V1 cell. Future kinds of work are decided when needed.
- A submission is ineligible without a **signed acceptance of the task document** and the M1b agreements.

## Build gotchas (easy to get wrong)
- **Spring Boot 4, not 3.** `platform/` is Spring Boot 4.1.1 → Spring Framework 7
  → **Jackson 3**, whose packages are `tools.jackson.*` (annotations stay
  `com.fasterxml.jackson.annotation`). Do not import `com.fasterxml.jackson.databind.*`.
- **Security is off by default** for local dev (`symmetry.security.enabled=false`).
  Set it true plus an OAuth2 issuer to require a JWT.
- **Never run untrusted code in a plain container or chroot.** The outer boundary
  is a KVM microVM. If `/dev/kvm` is absent, fail preflight; do not fall back.
- **Verify commands:**
  - `platform/`: `./mvnw -B verify` (the Testcontainers test needs Docker; it is
    skipped automatically when Docker is absent, so pure unit tests still run).
  - `ui/`: `npm ci && npm run build`.
  - `ai/`: `python -c "import app.main"`.
  - `wasm-sdk/`: `cargo build --release --target wasm32-unknown-unknown` (needs Rust).
  - `runner/`: not built yet; add a verify command with the first code and record
    it here before claiming anything is verified.

## Local Postgres (Docker)
- The dev database comes from `deploy/docker-compose.yml` (service `postgres`), published on `localhost:5432` — db/user/password all `symmetry`. It is tuned small for a ~2 GB machine (`shared_buffers=128MB`, `max_connections=40`, `mem_limit=512m`) with data checksums on. The schema is owned by the Flyway baseline `platform/src/main/resources/db/migration/V1__baseline.sql`.
- **Docker is rootful here** (socket `root:docker`). The user is in the `docker` group, so `docker ...` works after a fresh login. In a shell started before that change (or when unsure), wrap commands: `sg docker -c '<command>'` (this works in the current session). `sudo` also works.
- **Compose is the v2 plugin**, installed at `~/.docker/cli-plugins/docker-compose` (Debian's `docker.io` ships only buildx).
- Start / inspect / stop the dev DB:
  - `sg docker -c 'docker compose -f deploy/docker-compose.yml up -d postgres'`
  - `sg docker -c 'docker compose -f deploy/docker-compose.yml logs -f postgres'`
  - `sg docker -c 'docker compose -f deploy/docker-compose.yml down'` (add `-v` to wipe data)
  - `sg docker -c "docker exec symmetry-postgres psql -U symmetry -d symmetry"`
- Tests use **Testcontainers**, which also needs Docker access; if the shell lacks the group, run Maven as `sg docker -c './mvnw -B verify'`.

## Documenting changes (audit log) — do this when a change is done
- **Required:** when a code or design change is accepted, append one entry to `docs/past_final_commit_comments.md` (newest entry at the top). Do not make a change without documenting it.
- Each entry records: the date + short title (`(commit <hash>` or `(uncommitted)`), **what** changed, **why the decision was made** (the problem, the trade-off, the rejected alternative — this is what makes it auditable later), the files as `path:line`, and the code.
- **Modified code:** show **Before** and **After** for the exact lines changed.
- **New code:** paste it (or the key part) with a short comment and **line numbers** so it can be found again.
- Doc-only changes still get an entry; you may skip the Before/After code blocks.
- This is a **two-way door**: do it as part of the work, then report — no need to ask first.

## Working protocol
- **Two-way doors** (reversible): proceed, report after.
- **One-way doors** (irreversible/consequential): stop and ask first — one question at a time, with a recommendation. **"NO" is a valid answer.**
- If running a command could answer the question, run it instead of asking.
- Explain in plain language; the user is a novice programmer — avoid unexplained jargon.

> "Once I open a one-way door, it stays open for that work, including for any subagents you hand it to." — user
