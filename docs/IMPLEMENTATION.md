# Symmetry: Future of software tech work — How to Build the First Version (P1–P2 Plan)

This document explains the **first two stages** of the plan in
[`docs/DESIGN.md`](DESIGN.md). It is written for a beginner: every tool and
special word is explained the first time it appears.

The full technology choices (and why) live in [`docs/STACK.md`](STACK.md): a
**Java Spring Boot API**, a **React + AWS Cloudscape UI**, a **Python AI
service**, **Rust→WASM modules**, **PostgreSQL**, and **MinIO + Redis**.

We are building the part that proves the idea works: that a piece of code sent in
by a stranger can be **built**, **checked for correctness**, and **ranked** — all
without giving away the company's secrets.

We assume stage P0 is done: we have picked one kind of module, **image resizing**
(making an image bigger or smaller), and decided which language the existing code
uses (in this example, **Java**; Rust is built the same way — and the platform is
designed to accept any language the task committee names).

---

## 0. The tools we will use, and why

| Layer | Our choice | Why (in plain words) |
|---|---|---|
| The main website ("control plane") | **Java 21 + Spring Boot 4** | Spring Boot is a popular kit for building web servers in Java; it already matches the `oct/java/` folder. |
| Build tool for our own code | **Maven** (with a "wrapper") | Maven assembles our project and its libraries. A "wrapper" ships the tool *with* the project so the build does not depend on what is installed on the computer. |
| Database | **PostgreSQL 16+**, with **Testcontainers** for tests | A database is where we store records. We use Postgres for real in every environment (never H2 — it behaves differently and gives false confidence). |
| UI console | **React + AWS Cloudscape** | Amazon's own open-source design system gives the AWS-console look. |
| AI service | **Python + FastAPI** | Help for writing tasks and submissions, behind a model gateway. |
| Artifacts / queue | **MinIO (S3) + Redis** | Store submitted files; run and rate-limit evaluation jobs. |
| Worker languages | **Any language the task names** | For `optimize`, the committee sets it from the existing software. Java and Rust are simply the first two the platform can build; more are added through a **toolchain registry**. |
| Java build | **javac** (the standard Java compiler), version 21, fixed | Compiles the worker's Java.
| Rust build | **rustc** (the standard Rust compiler), fixed | Compiles the worker's Rust.
| Running untrusted Java | **A locked-down container (JVM tier)** | We run risky code inside an isolated box so it cannot harm anything. This is the *preferred* Java path. (Earlier we considered turning Java into WASM — a different format — but that throws away Java's speed tricks and measures the wrong thing. It is now a discouraged exception.) |
| Running untrusted Rust | **Chicory** running **WASM** | WASM is a safe, portable format. Chicory is a Java library that runs WASM code inside our Java program. A WASM module with *no* outside connections cannot touch the internet or files at all — safe by construction. |
| Property tests | **jqwik** (Java) / **proptest** (Rust) | Tools that test code with *many* randomly generated inputs, checking that a rule is *always* true. |
| Task documents | **Markdown (readable text) + a machine-readable checklist** | The worker reads it and accepts it before starting. |

**Why two ways to run code:** the platform has to handle more than one language, so
it has more than one "runner." Rust modules become WASM (very safe to run). Java
modules run in a locked-down container (also safe, and it keeps Java fast). This is
called being **polyglot** — able to work with more than one language.

**Not only two languages.** Java and Rust are just the first two entries in a
**toolchain registry**. Adding C, Go, Python, or TypeScript later means plugging in
a new compiler, not changing the center. (A second, quite different kind of work —
building brand-new apps, with proposals and hourly rates — is described in
[`NEW-BUILDS.md`](NEW-BUILDS.md), and comes after this plan.)

---

## 1. How the project is laid out (the folders)

A map of the folders, with a note on what lives where:

```
symmetry/
  docs/                        # these design documents
  deploy/                      # docker-compose: Postgres, MinIO, Redis, Keycloak
  platform/                    # the main website + the evaluator (one program)
    pom.xml                     # Maven's list of what to build and which libraries
    src/main/java/io/symmetry/
      PlatformApplication.java
      config/      # security wiring (JWT optional; off for local dev)
      contract/    # job-card object, rules, validator, disclosure, acceptance
      task/        # work kind + lifecycle labels (the "sockets")
      build/       # toolchain registry + Java/Rust connectors
      eval/        # keycard + checker/gate types (sandboxes land in M3)
      scoring/     # flexible metric vector (Pareto ranking lands in M5)
      # planned: submission/ , trust/ , store/ , api/
    src/main/resources/db/migration/   # Flyway V1, V2
    src/test/java/io/symmetry/         # our own tests
  ui/                          # React + AWS Cloudscape console (Vite)
  ai/                          # Python FastAPI AI service
  wasm-sdk/                    # Rust module SDK; modules/image-resize is the ABI example
  .github/workflows/           # CI: Maven, npm, pip, cargo
  # planned: agreements/ , contracts/ , tasks/ , examples/image-resize/ (M2) , workbench-cli/
```

(You do not need to memorize this. It is here so a builder knows where things go.)

---

## 2. M1 — The job card and the "I agree" step

**M1** means "milestone 1": the first chunk of work. M1 builds the contract
(the job card) and the acceptance flow.

**The job card, as a file.** Here is the same card from the design doc, as a file
the computer can read. The `language` line names the language from the registry (in
this example, `java`); for `optimize` work the committee sets it, and for `build`
work the worker proposes it:

```json
{
  "id": "module.image.resize",
  "version": "1.4.0",
  "taskDocument": "tasks/image-resize.md",
  "language": "java",
  "signature": {
    "name": "resize",
    "params": [{"name":"in","type":"Image"},{"name":"w","type":"u32"},
               {"name":"h","type":"u32"},{"name":"filter","type":"ResizeFilter"}],
    "returns": "Image"
  },
  "disclosureTier": "L1",
  "invariants": ["dims_match", "alpha_preserved", "channel_range_preserved"],
  "resourceBudget": {"timeMs":12,"peakMemMb":8,"artifactKb":64},
  "oracle": {"properties":"ref:properties/resize",
             "fuzzers":"ref:fuzz/resize",
             "holdout":"hidden","differential":"incumbent"},
  "scoring": {"gate":"all_pass",
              "rank":"pareto([p50,p99,peakMem])",
              "tiebreak":["artifactKb","complexity"]},
  "forbidden": ["net","fs","clock","random","threads"],
  "acceptance": "worker_must_accept_task_document",
  "license": "contributor-assignment"
}
```

**The task document, as a file.** It has plain requirements and a checklist:

```yaml
requirements:
  - "resize honors (w,h) exactly"
  - "alpha channel preserved"
  - "no network / filesystem / threads"
done_criteria: "all hidden property + holdout tests pass; artifact within budget"
runtime: <language set by the task>
```

**In our own code**, each of these files becomes a **record** — a simple,
unchangeable object in Java that holds the fields. We check the card is sensible
when we load it: the limits must be positive, the invariants must not be empty, the
language must exist in the toolchain registry, the tier must be L0, L1, or L2, and
the task document must not be empty. The job card also carries a **work kind**
(`compute` in V1; `service`/`ui`/`data`/`system` are reserved) and a **lifecycle
state** (`draft → negotiated → accepted → submitted → judged → merged`).

**The "I agree" step.** A worker calls an address to **accept** the task document.
We store a signed record: who accepted, the fingerprint of the exact task document
they saw, and that they accepted the required agreements. The signature is an
**Ed25519** signature (a built-in JDK 21 method) over a short message that binds the
worker to that **contract version** and document fingerprint. **If a submission has
no matching acceptance, it is refused** — and an acceptance whose signature does not
verify is rejected.

**The web addresses ("buttons") we build in M1:**

```
POST /contracts                 publish a contract + task document
GET  /contracts                 list all contracts
GET  /contracts/{id}/{version}  see one contract
GET  /contracts/{id}/{version}/workbench  see the public version (tier-filtered)
POST /contracts/{id}/accept     a worker accepts the task document (signed)
```

**Done when:** a contract and task document can be saved and read back; the accept
step is recorded; a submission without an acceptance is refused.

---

## 3. M1b — Deciding what may be published, and the worker's rules

Not every module is safe to publish. Because workers are outside people sending in
code, we must first decide **what is safe to reveal**, and the worker must accept
**responsibility rules**.

**The disclosure assessment** is a small checker (`disclosure/DisclosureAssessor`)
that produces a verdict: *may this be published, at which tier, and why?* The
submitting company **must** obtain and own this assessment. We must never let the
platform become a careless pipe that strips a builder of their rights over code
they already wrote.

What the checker looks at, and the question it asks:

| It looks at | The question |
|---|---|
| the module | Is it already built? Internal only? Based on open-source code? |
| the shape (signature) | Does the shape give away the architecture? |
| the data | Does the sample data contain someone else's content or private data? |
| the scope | Is what is shown enough to reverse-engineer the whole build? |

It outputs: *can we publish? at which tier? which agreements are required? why?*

**The agreements the worker must accept** (each one a separate, explicit click —
never buried in a long legal wall of text):

- **Honesty: no hidden traps.** The code is the worker's own; no backdoors, no
  secret phone-home, no "time bombs" that break later.
- **Use for this job only.** The worker's code is used for the stated module. The
  worker gets no claim over the rest of the company's system beyond the agreed license.
- **AI help must be declared.** AI help is allowed, but the worker owns
  correctness and must say when AI wrote a lot of it. No blindly pasted "vibe code."
- **Keep it secret.** The worker will not publish, leak, copy, or reuse the
  company's secret shape or data outside the task.
- **Responsibility.** The worker is not shielded from the consequences of
  malicious or careless code.
- **License choice.** The worker picks, explicitly, what rights they grant —
  instead of a hidden default that quietly takes rights over their existing work.

**Done when:** a module whose assessment is "do not publish" cannot be published;
a submission missing the required agreements is refused; submissions that lean
heavily on AI are flagged for a human to review.

**Status:** the agreements-on-accept gate and the **tier-filtered workbench** are in
place (`DisclosureService`, `GET /contracts/{id}/{version}/workbench`); the
`DisclosureAssessor` itself (the verdict that decides *whether* a module may be
published) is the next M1b task.

---

## 4. M2 — The first practice module and the hidden answer key

In `examples/image-resize/` we build a complete practice setup:

- `baseline/` — the working old version, in the existing language.
- `properties/` — rules that must *always* hold. Examples: the output size matches
  what was asked; the see-through part stays see-through; color values stay in
  range; resizing to the same size changes nothing.
- `fuzz/` — a starting pile of inputs, plus a machine that mutates them to make
  new odd inputs.
- `holdout/` — the **secret exam**: inputs and the correct answers. These are
  **not** stored in the public project; they load only on the evaluator's side.
- `ood/` — "trick" inputs that sit outside the normal pattern, to catch code that
  only works on typical data.
- `acceptance/` — the company's checklist used to check the finished build meets
  the goal.

**A caution:** the property tests must be *necessary* (each one really matters) and
*independent* (they are not just copies of each other), or they give false comfort.

**Done when:** the old version passes its own answer key; a deliberately-broken
module fails at least one checkpoint; the secret exam cannot be read from the
public workbench.

---

## 5. M3 — The builders and the sandboxes (the heart of P2)

**The build service** turns source code into something runnable:

- **JavaBuilder** runs `javac` (version 21, fixed) on the worker's Java.
- **RustBuilder** runs `rustc` (fixed) to produce WASM.
- Both must produce the **same result every time** from the same source. We
  fingerprint (hash) the result and remember which tool version made it.

**The sandbox** is where untrusted code runs. We have two:

- **WASM tier (Chicory):** we load the WASM module in a fresh mini-computer for
  each run. We register **no outside connections at all**, so the code physically
  cannot use the internet, files, the clock, randomness, or extra threads. We give
  it a **fuel limit** (a budget of "steps" it may take, like a game meter) and a
  **memory limit** (a cap on how much it may remember). A wall clock timer is a
  backup. When fuel runs out, the module stops.
- **JVM tier (for Java that cannot become WASM):** the Java runs inside a separate
  locked-down container with no internet, a read-only disk, and a supervisor
  watching it. This is slightly weaker isolation, so we label those results as such.

Both tiers follow the same rules:

- **Determinism check:** run the same input twice. If the two results differ, the
  module is rejected — it is not predictable.
- A crash, an out-of-memory, or running out of fuel is treated as a normal
  "failed the checkpoint," never as something that breaks our system.

**Done when:** code that tries to connect to the outside fails to start; an
infinite loop hits the fuel limit; an unpredictable module is rejected; three runs
of a good module give identical results; and submissions in the languages we have
built (Java and Rust) work.

---

## 6. M4 — The gate (the checkpoint line)

The **gate** runs the checkpoints in order and stops at the first failure:

1. `buildOk` — it compiles with the fixed tool, and the result is repeatable.
2. `staticOk` — it uses no forbidden things, and the file is within the size budget.
3. `propertyHunter` — the "always true" rules hold.
4. `fuzzHunter` — it survives lots of odd random inputs.
5. `differentialHunter` — it behaves the same as the old code on held-back inputs
   (allowing a small, agreed tolerance).
6. `holdoutHunter` — it passes the secret exam.
7. `oodHunter` — it survives the trick inputs.
8. `goalMatch` — the finished build meets the company's checklist.

The result is a **GateResult**: pass or fail, which step failed, sanitized
explanations, and the numbers. Crucially, the explanation a worker sees contains
**none** of the secret data and **none** of the old code.

**Done when:** three runs give the same verdict; the sanitized explanation leaks
nothing; each checkpoint is proven to catch a module built specifically to fail it.

---

## 7. M5 — Ranking and the leaderboard

Each passing submission gets a **MetricVector** — a small bundle of numbers:
median speed (p50), worst-case speed (p99), peak memory, file size, and an energy
estimate placeholder.

- Only submissions that **passed the gate** are ranked.
- The **Pareto** rule decides the leaderboard: a part stays on the list unless some
  other part is at least as good in *every* number and strictly better in one.
- Ties are broken by file size, then by how simple the code is, then by a human look.
- **Anti-cheating:** the secret questions rotate each round; trick inputs are used;
  the comparison against the old code always runs; and no number a worker reports
  about themselves is ever trusted.

**Done when:** with three prepared submissions, the leaderboard ranks them
correctly — the clearly-worse one is dropped, and a tie is settled by the tiebreak.

---

## 8. M6 — The submit-to-verdict flow (end to end)

The final piece connects everything:

```
POST /submissions               {contractId, version, language, acceptanceId, source, signature}
GET  /submissions/{id}          status
GET  /submissions/{id}/build    build status + artifact fingerprint
GET  /submissions/{id}/verdict  gate result + Pareto numbers (sanitized)
GET  /contracts/{id}/{version}/leaderboard
```

Source is stored by fingerprint (so identical code is stored once). Builds are
fingerprinted. Verdicts are signed. The full journey is:
**accept → submit → build → gate → score → verdict.**

**Done when:** the whole journey works for the practice module in the languages we
have built so far (Java and Rust); a broken module is rejected at the expected step;
the leaderboard shows the correct Pareto order.

---

## 9. How we test our own system

| Level | Tool | What it checks |
|---|---|---|
| Unit tests | JUnit 5 | small pieces: contract rules, acceptance gating, Pareto math, tier filtering |
| Property tests | jqwik | "always true" rules and scoring behavior |
| Integration tests | Spring Boot Test + Testcontainers Postgres | the web addresses work end to end, including the accept flow |
| Adversarial tests | hand-built bad modules (Java + Rust) | forbidden access, infinite loops, unpredictability, overfitting to the exam |
| Reproducibility | repeated runs | the same source always gives the same verdict |

**CI** means "continuous integration" — an automatic check that runs whenever code
changes. We run a Docker build, then `./mvnw verify`, which runs the unit, property,
and integration tests.

---

## 10. "We are done with P2 when"

- One contract completes the full accept→submit→build→verdict journey.
- **The languages we have built so far (Java and Rust)** submissions build and are
  evaluated, and the registry makes adding a third a matter of plugging in a compiler.
- The same source gives the same verdict across three runs.
- There are no side doors: the internet, files, clock, randomness, and extra
  threads are impossible or blocked.
- Every checkpoint catches the bad module built to trip it.
- The explanation a worker sees leaks no secret data, and missing acceptance or
  agreements block entry.

When all of these hold, P2 is finished and P3 (locked safe room, signing, audit
ledger) can begin.

---

## 11. Risks, and how we reduce them

| Risk | How we reduce it |
|---|---|
| Java does not compile cleanly to WASM | Prefer the JVM-tier container for Java; label its weaker isolation in results. |
| The container is weaker isolation than WASM | Prefer the WASM path; send high-value contracts only to WASM-eligible modules. |
| Memory numbers differ between tiers | Report a "relative" number and label it per tier. |
| Builds are not repeatable (javac/rustc drift) | Fix tool versions; fingerprint results; reject if the fingerprint drifts. |
| Overfitting to the secret exam | Rotate questions, use trick inputs, always compare to the old code. |
| A malicious AI-generated submission | Require the AI declaration, add human review, use trick inputs. |
| The answer key is too weak | An eligibility filter: modules we cannot verify are not allowed in. |

---

## 12. What to do next (in order)

1. Set up `platform/` with a Maven wrapper and a basic Spring Boot skeleton.
2. Build the contract and task-document objects, their checks, the acceptance
   gating, and the M1 web addresses.
3. Build the disclosure checker and the agreements (M1b).
4. Add the first practice module and the old version in its existing language (M2).
5. Build the compilers (JavaBuilder, RustBuilder) behind the toolchain registry, and
   the WASM sandbox (M3).

**A decision still open from P0:** the first practice module and its language —
default to **Java** (it matches the `oct/java/` folder), with the Rust path built
the same way. The `build` track (new apps, proposals, rates) is a later phase; see
[`NEW-BUILDS.md`](NEW-BUILDS.md).
