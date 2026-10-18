# AGENTS.md

## Repo state
- **Design-only.** Four markdown files, no code, no build/test/lint tooling, not a git repo.
- Do **not** invent or run build/test commands — none exist yet. Add tooling before claiming anything is verified.

## Source of truth (priority order)
1. `README.md` — overview
2. `docs/DESIGN.md` — full design
3. `docs/IMPLEMENTATION.md` — P1–P2 plan
4. `docs/FUTURE-PROOFING.md` — design space & the seven sockets (= seams)

Decisions are repeated across these docs. When you change one, reconcile the others.

## Host toolchain
- Available: **Java 21** (`javac`), **Node 20**, **Python 3.13**, **Docker**.
- Absent: **Maven, Gradle, Rust/cargo**. Any Java build needs a committed wrapper (e.g. `./mvnw`) or Docker — do not assume `mvn` or `cargo` exist.

## Load-bearing decisions (easy to get wrong)
- Worker language is **Java OR Rust**, chosen by the worker to match the existing proprietary module. Both are first-class.
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
