# Past final-commit comments — code change audit log

This file is the **audit log** for accepted code changes. Each time a
change is accepted, we add one entry below. Later, anyone can read an entry and
understand **what changed**, **why the decision was made**, and **see the code
before and after**.

- **Written for:** future you (and any new developer) auditing decisions.
- **Rule:** every accepted change gets an entry — no change without a note.
- **Plain language:** this repo is explained simply; keep entries simple too.

---

## How to add an entry (procedure)

When a change is finished and accepted, add an entry **at the top** of
"Entries" (newest first) using this shape:

1. **Heading:** `## <date> — <short title> (<commit or "uncommitted">)`.
2. **What changed:** one or two sentences in plain words.
3. **Why (decision):** the reason the source code was changed — the problem it
   solves, the trade-off accepted, and any alternative we rejected. This is the
   part that makes the decision auditable later.
4. **Files touched:** list each file as `path:line`.
5. **Code — modified files:** show **Before** and **After** (the exact lines that
   changed).
6. **Code — new files:** paste the new code (or the key part) and add a short
   comment plus **line numbers**, so it can be found again.
7. **References:** link related docs, issues, or the previous entry.

> If a change only edits documentation (no `src` code), still add an entry, but
> you may skip the Before/After code blocks and just say which docs changed.

---

## Entries

## 2026-10-19 — Add draw.io visual diagrams (boxes, solid/dotted lines) (uncommitted)

**What changed.** Added `docs/diagrams/symmetry-uml.drawio`, an 11-page draw.io
document built from native draw.io objects: actor shapes, use-case ellipses, UML
class boxes (name/attributes/methods compartments), deployment and component
boxes, an ER entity pair with a crow's-foot edge, and sequence diagrams with
lifelines and message arrows. Connector convention: **solid = implemented**,
**dashed/dotted = planned**. The markdown doc now points to it.

**Why (decision).** The owner asked for the class and other diagrams to be
"boxes and dotted and solid lines" using draw.io objects, rather than text-style
Mermaid. A `.drawio` file was chosen because it opens and stays editable in
draw.io / diagrams.net, and diff-friendly plain XML (`<mxfile>` with one
`<diagram>` per page) keeps it in the repo next to the code. Rejected
alternatives: exported PNG/SVG (not editable, goes stale) and a screenshot from
the web app (no source). The Mermaid copy is kept because it renders on GitHub;
the draw.io file is the editable visual source.

**Files touched.**
- `docs/diagrams/symmetry-uml.drawio:1` (new — 11 pages).
- `docs/diagrams/uml_diagrams.md:15` (added pointer to the `.drawio`).

**Code.** Documentation/diagram-only change; Before/After blocks skipped per the
rule above.

**References.** Entry below (UML/system-interaction doc); `docs/DESIGN.md`,
`docs/IMPLEMENTATION.md`, `docs/STACK.md`.

## 2026-10-19 — Add UML / system-interaction design doc (uncommitted)

**What changed.** Added `docs/diagrams/uml_diagrams.md`: a class-first UML and
system-interaction reference. It contains a use-case catalog, context/deployment
and component diagrams, the domain class model, an ER diagram of the schema, a
per-class interaction diagram for **every** class (platform, UI, AI, WASM SDK)
showing its software interfaces, database access, and use case, plus use-case
sequence diagrams (implemented and planned) and a traceability matrix.

**Why (decision).** The owner asked for "senior system engineer"-level diagrams
that walk one class at a time, cover interactions with software interfaces,
database access, and stakeholder use cases. A single markdown file with Mermaid
was chosen because it renders directly on GitHub and next to the code it
describes (no external diagram tool to install or keep in sync). Rejected
alternative: generating PNG/SVG images or a heavyweight UML tool — harder to
review in pull requests and to keep current. Implemented (✅) and planned (🔜)
elements are marked separately so the diagram is honest about M1–M1b status.

**Files touched.**
- `docs/diagrams/uml_diagrams.md:1` (new — full document).

**Code.** Documentation-only change; Before/After blocks skipped per the rule
above.

**References.** `docs/DESIGN.md`, `docs/IMPLEMENTATION.md`, `docs/STACK.md`,
`AGENTS.md`; entry below (Postgres/schema hardening).

## 2026-10-19 — Run Postgres in Docker; squash the schema into one hardened baseline (uncommitted)

**What changed.** Started the dev Postgres from `deploy/docker-compose.yml` (tuned
for a ~2 GB machine), squashed the old `V1` + `V2` migrations into a single
well-designed `V1__baseline.sql`, and made one small code change that the new
table rules require.

**Why (decision).** The owner asked to run a real Postgres locally and to apply
senior DBA table-design practices. The earlier migrations had never been applied
to any persistent database (no `symmetry` container or volume existed), so
squashing into one clean baseline was **safe and clearer** than layering a `V3`
on top of already-weak definitions — accepted trade-off: this rewrites
migration history, which we only did because nothing was ever deployed. Rejected
alternative: keep `V1`/`V2` untouched and add `V3` (preserves history but leaves
the weak definitions in the schema forever).

Table-design rules applied (see the file's header comment): UUID PKs defaulted
with `gen_random_uuid()`; `TIMESTAMPTZ` everywhere; enums as `TEXT` + named
`CHECK`; `NOT NULL` plus "not blank" checks; JSONB shape-checked
(object/array); `ON DELETE CASCADE` on the acceptance→contract foreign key;
`updated_at` kept correct by a trigger; **only** indexes that match real queries
(no redundant ones). The new unique `(contract_id, worker_id)` rule means the
accept path needs to turn a duplicate into a `409`, so `ContractService.accept`
was updated to flush inside a `try` and translate the violation.

**Files touched.**
- `platform/src/main/resources/db/migration/V1__baseline.sql` (new; replaces
  `V1__contract_acceptance.sql` + `V2__work_kind_lifecycle_verified_acceptance.sql`)
- `platform/src/main/java/io/symmetry/contract/ContractService.java:111`
- `platform/src/test/java/io/symmetry/contract/ContractServiceTest.java:175`
- `platform/src/test/java/io/symmetry/e2e/AcceptanceJourneyE2ETest.java`
- `deploy/docker-compose.yml:15`
- `AGENTS.md` (new "Local Postgres (Docker)" section)
- `docs/STACK.md:185`, `docs/IMPLEMENTATION.md:72` (reconciled migration names)

**Code — modified files.**

`platform/src/main/java/io/symmetry/contract/ContractService.java`

Before (in `accept`, lines 130–134):
```java
        Acceptance acceptance = new Acceptance(UUID.randomUUID(), contract.getId(), request.workerId(),
                hash, request.signature(), request.publicKey(), true, writeJson(request.agreements()));
        contract.markAccepted();
        contracts.save(contract);
        return acceptances.save(acceptance);
    }
```
After (lines 130–142):
```java
        Acceptance acceptance = new Acceptance(UUID.randomUUID(), contract.getId(), request.workerId(),
                hash, request.signature(), request.publicKey(), true, writeJson(request.agreements()));
        contract.markAccepted();
        contracts.save(contract);
        try {
            // Flush now so the unique (contract_id, worker_id) constraint is checked
            // inside this transaction and can be reported as 409, not a 500.
            return acceptances.saveAndFlush(acceptance);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Worker " + request.workerId() + " already accepted contract " + contractId);
        }
    }
```

`platform/src/test/java/io/symmetry/contract/ContractServiceTest.java`

Before (line 175):
```java
        when(acceptances.save(any(Acceptance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
```
After (line 175):
```java
        when(acceptances.saveAndFlush(any(Acceptance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
```

`platform/src/test/java/io/symmetry/e2e/AcceptanceJourneyE2ETest.java` — replaced
`TestRestTemplate` (Boot 4 moved it and it needs an extra `spring-boot-restclient`
class that is not on the classpath) with Spring `RestClient` bound to
`@LocalServerPort`, configured with a no-op status handler so 4xx/5xx are
asserted instead of thrown. No new dependency added.

`deploy/docker-compose.yml` — the `postgres` service (line 15) now sets
`POSTGRES_INITDB_ARGS="--data-checksums"` (line 24), a low-RAM `command` block
(line 27: `shared_buffers=128MB`, `effective_cache_size=512MB`, `work_mem=4MB`,
`maintenance_work_mem=64MB`, `max_connections=40`, slow-query logging),
`shm_size: 128mb` (line 42), and `mem_limit: 512m` (line 44).

**Code — new files.**

`platform/src/main/resources/db/migration/V1__baseline.sql` (new). Key lines:
- `18` `CREATE TABLE contract` — UUID PK `DEFAULT gen_random_uuid()`; `payload JSONB`; `created_at`/`updated_at TIMESTAMPTZ`.
- `43` `uq_contract_module_version UNIQUE (module_id, version)` — also serves list-by-module (leading column), so no separate module index.
- `45`–`62` named `chk_contract_*` checks (task kind, tier, work kind, lifecycle, not-blank fields, payload is an object).
- `69` `idx_contract_payload` — `GIN (payload jsonb_path_ops)` for payload lookups.
- `71` `CREATE TABLE acceptance` — FK `contract_id` with `ON DELETE CASCADE` (line 88).
- `93` `uq_acceptance_contract_worker UNIQUE (contract_id, worker_id)` — one acceptance per worker per contract; also serves `findByContractId`.
- `102` `idx_acceptance_worker_id` — "everything this worker accepted".
- `105`–`115` `set_contract_updated_at()` function + `trg_contract_set_updated_at` trigger.
- `117`–`127` `COMMENT ON` table/column notes.

**References.** Supersedes the `V1__*.sql, V2__*.sql` row in the 2026-10-18 entry.
Verified: `sg docker -c './mvnw -B verify'` → BUILD SUCCESS, **30 tests, 0 skipped**
(with Docker, integration + E2E run for real). The dev stack applied `V1` on
startup, `\d contract`/`\d acceptance` showed every constraint/index, and the
`updated_at` trigger fired (`updated_at > created_at` after an `UPDATE`).

---

## 2026-10-19 — Introduce this audit log + record the change rule (uncommitted)

**What changed.** Created `docs/past_final_commit_comments.md` (this file) and
added a "Documenting changes (audit log)" section to `AGENTS.md`. Also fixed the
stale "Design-only / no code" text in `AGENTS.md`'s Repo state.

**Why (decision).** Decisions were being made in code with no durable, reviewable
record of *why*. The fix is a lightweight, mandatory change log written in plain
language so any future reader can audit a decision. Rejected alternative: relying
on git commit messages alone — they capture *what* but rarely the *why*, the
trade-off, or the rejected alternative.

**Files touched.**
- `docs/past_final_commit_comments.md:1` (new)
- `AGENTS.md` — new "Documenting changes (audit log)" section; Repo state updated.

**Code — modified files.** None (documentation only).

**Code — new files.** This document; the procedure is the "How to add an entry"
section above (`docs/past_final_commit_comments.md:14`).

**References.** `AGENTS.md` → "Documenting changes (audit log)".

---

## 2026-10-19 — Remove `FUTURE-PROOFING.md`; strip "socket" references from code (uncommitted)

**What changed.** Deleted `docs/FUTURE-PROOFING.md` and removed every reference
to it, including the "(socket N in `docs/FUTURE-PROOFING.md`)" notes in six Java
source files.

**Why (decision).** The product owner decided the future work will be **decided
when it is needed**, so keeping a standing "future-proofing" document risks
drifting out of date and implying commitments we have not made. The *ideas*
(keep interfaces cell-agnostic; add languages via a toolchain registry) are still
true and still in the code and in `docs/STACK.md` / `docs/NEW-BUILDS.md` — only
the separate document and the cross-references were removed. Rejected
alternative: leaving the file and marking it "draft", which would still leave a
stale document to maintain.

**Files touched.**
- `docs/FUTURE-PROOFING.md` (deleted)
- `README.md:107`, `AGENTS.md:14`, `AGENTS.md:30`, `docs/STACK.md:199`,
  `docs/NEW-BUILDS.md:46`
- `platform/src/main/java/io/symmetry/task/WorkKind.java:4`
- `platform/src/main/java/io/symmetry/task/LifecycleState.java:4`
- `platform/src/main/java/io/symmetry/scoring/MetricVector.java:9`
- `platform/src/main/java/io/symmetry/eval/Keycard.java:5`
- `platform/src/main/java/io/symmetry/eval/Checker.java:4`
- `platform/src/main/java/io/symmetry/build/Toolchain.java:4`

**Code — modified files (the intent of each class is unchanged; only the stray
file reference was dropped).**

`platform/src/main/java/io/symmetry/task/WorkKind.java`

Before (line 4):
```java
 * The kind of work a contract describes (socket 1 in {@code docs/FUTURE-PROOFING.md}).
```
After (line 4):
```java
 * The kind of work a contract describes.
```

`platform/src/main/java/io/symmetry/task/LifecycleState.java`

Before (line 4):
```java
 * The states a contract moves through (socket 7 in {@code docs/FUTURE-PROOFING.md}).
```
After (line 4):
```java
 * The states a contract moves through.
```

`platform/src/main/java/io/symmetry/scoring/MetricVector.java`

Before (lines 9–10):
```java
 * A flexible bundle of named measurements (socket 4 in
 * {@code docs/FUTURE-PROOFING.md}).
```
After (line 9):
```java
 * A flexible bundle of named measurements.
```

`platform/src/main/java/io/symmetry/eval/Keycard.java`

Before (lines 5–6):
```java
 * The capabilities a module is allowed to use while running (socket 5 in
 * {@code docs/FUTURE-PROOFING.md}).
```
After (line 5):
```java
 * The capabilities a module is allowed to use while running.
```

`platform/src/main/java/io/symmetry/eval/Checker.java`

Before (line 4):
```java
 * A single checkpoint in the gate (socket 3 in {@code docs/FUTURE-PROOFING.md}).
```
After (line 4):
```java
 * A single checkpoint in the gate.
```

`platform/src/main/java/io/symmetry/build/Toolchain.java`

Before (line 4):
```java
 * A compiler connector (socket 6 in {@code docs/FUTURE-PROOFING.md}).
```
After (line 4):
```java
 * A compiler connector.
```

**Code — new files.** None.

**References.** Decision recorded in `AGENTS.md` ("future kinds of work are
decided when needed"). Reconciled per the AGENTS.md rule "when you change one,
reconcile the others".

---

## 2026-10-18 — M1 walking skeleton: initial backend + UI + tests (commit `0b62a45`)

**What changed.** First working slice: the `platform/` Spring Boot API (publish a
contract, browse it, read a tier-filtered workbench, sign and accept a task
document), a Cloudscape `ui/` contracts table, an `ai/` FastAPI stub, a
`wasm-sdk/` ABI crate + example module, plus a Postgres/Testcontainers test suite.

**Why (decision).** This is the smallest end-to-end proof of the core promise —
"verify without leaking" — so the money-and-trust path (signed acceptance) is
real before we build more. Key choices and reasons:

- **Postgres + Testcontainers, never H2:** the schema needs `jsonb`, exact money,
  and timezone-correct timestamps that H2 cannot faithfully model (see
  `AGENTS.md`).
- **Signed acceptance of the task document:** a submission is ineligible without
  it, so the hash and the worker's Ed25519 signature are bound together.
- **Security off by default:** local development should not require an OAuth2
  server; turn it on with `symmetry.security.enabled=true`.
- **Interfaces stay cell-agnostic:** V1 only does compute, but the work kind,
  lifecycle, toolchain registry, keycard, checker list, and flexible metrics
  are already types, so a new kind of work plugs in instead of rewriting the
  centre.

**Files touched (all new in this commit).** The decision-bearing code:

| File | Key lines | Comment (what it does) |
|---|---|---|
| `config/SecurityConfig.java` | `21`, `23` | Toggleable security: permit-all by default, JWT when enabled. |
| `contract/ContractController.java` | `32`, `38`, `49`, `54`, `59` | The five REST endpoints (`POST/GET /contracts`, get, workbench, accept). |
| `contract/ContractService.java` | `28`, `29`, `112` | Task-kind + tier allow-lists; `accept(...)` is the signed, gated flow. |
| `contract/ContractService.java` | `123`–`124` | Binds the task-document hash into the canonical accept message. |
| `contract/MessageSigner.java` | `29`, `40`, `52` | `sha256`, `canonicalAcceptMessage`, and Ed25519 `verify`. |
| `contract/ContractDocumentValidator.java` | `1`–`69` | Rejects documents missing sections / with bad numbers. |
| `contract/DisclosureService.java` | `1`–`49` | Per-tier view: secrets are stripped before leaving the server. |
| `contract/Acceptance.java` | `20`, `53` | Stored acceptance (hash, public key, signature, verified flag). |
| `task/WorkKind.java` | `10`–`15` | Work kinds; V1 uses `COMPUTE` only. |
| `task/LifecycleState.java` | `10`–`16` | Full state sequence; V1 walks `DRAFT → ACCEPTED`. |
| `eval/Keycard.java` | `18` | `EMPTY` keycard for V1 (no network/filesystem/clock/threads). |
| `eval/Checker.java` | `10` | The gate is a list of checkers, not a fixed sequence. |
| `build/Toolchain.java`, `build/ToolchainRegistry.java` | `11`, `1`–`47` | Languages are registered, not hardcoded. |
| `scoring/MetricVector.java` | `16` | Metrics are a named map, so new domains need no schema change. |
| `resources/db/migration/V1__*.sql`, `V2__*.sql` | `1`–`27`, `1`–`16` | Schema for contracts and acceptances. |

**Code — modified files.** None (everything here was new).

**Code — new files with line numbers and comments.** See the table above; the
`socket` doc-comments these files pointed to were removed in the 2026-10-19 entry.

**References.** `docs/STACK.md` §7 (build status), `docs/IMPLEMENTATION.md`
(M1/M1b). Test suite: `platform/src/test/java/io/symmetry/` (unit, integration,
and `e2e/AcceptanceJourneyE2ETest.java`).
