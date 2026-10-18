# AGENTS.md

## Repo state
- **Design-only.** Markdown docs only — no code, no build/test/lint tooling.
- It **is** a git repo now (branch `main`, remote `origin` → GitHub `arun246/symmetry`).
- Do **not** invent or run build/test commands — none exist yet. Add tooling before claiming anything is verified.

## Source of truth (priority order)
1. `README.md` — overview
2. `docs/DESIGN.md` — full design
3. `docs/NEW-BUILDS.md` — the `build` track (new apps: proposals, rates, milestones, quality bar)
4. `docs/IMPLEMENTATION.md` — P1–P2 plan
5. `docs/FUTURE-PROOFING.md` — design space & the seven sockets (= seams)

Decisions are repeated across these docs. When you change one, reconcile the others.

## Host toolchain
- Available: **Java 21** (`javac`), **Node 20**, **Python 3.13**, **Docker**.
- Absent: **Maven, Gradle, Rust/cargo**. Any Java build needs a committed wrapper (e.g. `./mvnw`) or Docker — do not assume `mvn` or `cargo` exist.

## Load-bearing decisions (easy to get wrong)
- Language is **not fixed to Java/Rust**. For `optimize`, the task committee sets it from the existing software (any language); for `build`, the worker proposes it. Java and Rust are just the first two **toolchain-registry** entries.
- Two `task_kind`s: **`optimize`** (improve an existing module) and **`build`** (brand-new app: design proposal + hourly/fixed rate + milestones). See `docs/NEW-BUILDS.md`; V1 ships only `optimize`.
- **Java evaluation = JVM-tier sandbox** (isolated process/container). Java→WASM (TeaVM) is a *discouraged narrow exception*, not the default — it drops the JIT and measures the wrong target.
- **Rust evaluation = `wasm32` in Chicory** (no host imports, fuel/memory metered).
- **Correctness is a gate**; resource metrics only rank submissions that already pass.
- V1 ships **one workload cell (compute kernels)**, but core interfaces must stay cell-agnostic. Install the **seven sockets** in `docs/FUTURE-PROOFING.md` before writing code; do not hardcode the V1 cell.
- A submission is ineligible without a **signed acceptance of the task document** and the M1b agreements.

## Working protocol
- **Two-way doors** (reversible): proceed, report after.
- **One-way doors** (irreversible/consequential): stop and ask first — one question at a time, with a recommendation. **"NO" is a valid answer.**
- If running a command could answer the question, run it instead of asking.
- Explain in plain language; the user is a novice programmer — avoid unexplained jargon.

> "Once I open a one-way door, it stays open for that work, including for any subagents you hand it to." — user
