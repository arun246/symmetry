# Symmetry: Future of software tech work — Design Document

This is the full design. It is written to be read by **anyone who is going to
build it**, even if you are new to programming. Every special word is explained
the first time it appears. If you only want the short version, read
[README.md](../README.md) first.

---

## 1. What we are building

Think of a big software system as a machine with many parts. Each part does one
job. We call each part a **module**.

Companies have **proprietary** modules — code they own and keep secret. Sometimes
a company wants one of these modules to be **better**: faster, smaller, or cheaper
on memory. But they cannot show the secret code to strangers.

Symmetry lets them ask the outside world for help **without giving away the
secret**. The company publishes a **contract** (a machine-readable job card) and a
**task document** (plain-language instructions). An outside **worker** picks the
language that matches the company's existing module — **Java or Rust** — accepts
the task document, and submits source code. The platform builds and tests that
code privately. Correct submissions are ranked by speed and memory. The best one
is paid and added to the company's system.

**Target:** proprietary modules.
**How we measure success:** correctness first, then speed and memory.

---

## 2. The one hard problem (and how we handle it)

There are two things we want, and they fight each other:

1. We want to **check** that the submitted code is correct.
2. We want to **hide** the company's secrets and its hidden test data.

Showing the full test suite would leak the secret. Hiding everything would make
checking impossible. Our answer is to **carefully split** what the worker can see
from what the hidden answer key knows:

| Layer | What the worker sees | What the hidden answer key keeps |
|---|---|---|
| Function shape (signature) | everything | — |
| Task document (requirements) | everything; must be accepted | the exact hidden test list |
| Test data | a few made-up samples | the real data plus secret exam questions |
| Scores | their own local numbers | hidden comparison against the old code |

We never make the "secret box" case easy. We make it **bounded** (we control how
much moves) and **enforceable** (we can prove the rules were followed).

**A key rule falls out of this:** if a module's correctness *cannot* be written
down as clear, checkable tests, then that module is **not allowed** into Symmetry.
We would rather reject it than pay for code we cannot verify. Think of it like a
sports league: if we cannot score the game, we do not play it.

---

## 3. Rules the design must never break

These are the promises that keep the system honest.

1. **Correctness is a gate, never a score.** Fast-but-wrong loses to slow-but-right.
2. **The language follows the existing module.** The worker picks Java or Rust so
   the finished part drops straight into the company's codebase. The task document
   fixes which language is allowed.
3. **No work without consent.** A worker must explicitly accept the task document
   before their submission counts. The acceptance is recorded, like signing a form.
4. **Never move the secret data — move the checker.** Testing happens where the
   secret lives, not on a central server the company does not control.
5. **The contract and task document are the product.** They are the interface, the
   onboarding, and the legal boundary all at once.
6. **Scores must resist cheating.** If you pay for "fewest lines of code," people
   write unreadable one-liners. So we use random secret questions, compare against
   the old code, and never trust numbers a worker reports about themselves.
7. **Everything is signed and traceable.** From acceptance to submission to merge,
   we keep a record of what happened and who did it.
8. **Fail closed.** If anything is unclear — an unknown system call, a build that
   will not repeat exactly, a tampered answer key — we reject. We do not guess.

---

## 4. How the whole thing is wired together

Here is the same diagram, then explained box by box.

```
        ┌─────────────────────── Platform Control Plane (SaaS) ───────────────────────┐
        │  Contract Registry │ Task Docs │ Identity/Reputation │ Leaderboard │ Escrow │ Audit │
        └───────┬─────────────────────┬───────────────────────┬───────────────────────┘
                │                     │                       │
     ┌──────────▼─────────┐  ┌────────▼─────────┐   ┌─────────▼──────────┐
     │ Worker Workbench     │  │ Synthetic Data   │   │ Integration SDK    │
     │ CLI + web IDE        │  │ Service          │   │ (company repo side)│
     │ Java or Rust (chosen)│  │                  │   │                    │
     └──────────┬──────────┘  └──────────────────┘   └─────────┬──────────┘
                │ submit signed source + accepted task doc      │ pull winner
                ▼                                               ▼
        ┌──────────────────────── Eval Fabric (federated) ────────────────────────┐
        │  Build: javac (JDK 21) │ rustc (pinned)  → bytecode / wasm / binary       │
        │  Platform Runners (JVM tier + WASM/Chicory tier)  │  Company Eval Node   │
        │  · unit/property/fuzz · differential vs incumbent │  (TEE / on-prem)     │
        │  · binary-vs-goal match · resource metering       │  · hidden corpus +   │
        │                                                   │    holdout + canaries│
        └───────────────────────────┬──────────────────────────────────────────────┘
                                     ▼  signed verdict + Pareto metrics (tests never leave)
                              Compile → Gate → Score → Leaderboard → Payout
```

**The Platform Control Plane** is the main website. It holds a **registry**
(the list of published contracts), the **task documents**, **identity and
reputation** (who a worker is and how trusted), the **leaderboard** (rankings),
**escrow** (money held safely until a job is done), and an **audit ledger** (a
tamper-evident history of events). We call it a "control plane" because it does not
do the heavy work itself — it *coordinates*.

**The Worker Workbench** is the tool a worker uses to do the job: a command-line
tool and a web editor. It can fetch the public part of the contract and some
made-up sample data, but never the secrets. The worker chooses Java or Rust here.

**The Synthetic Data Service** makes fake-but-realistic sample data. Think of
"practice questions" that feel like the real exam but are not the real exam.

**The Integration SDK** is a small toolkit the company runs on its own codebase.
It helps them pull a winning module back into their system.

**The Eval Fabric** is the group of machines that build and test submissions. It
is **federated**, meaning it can run in more than one place at once:
- **Platform Runners** run the *public* checks (build it, run simple tests, compare
  against the old code, measure speed/memory).
- **Company Eval Node** runs the *secret* checks (the real hidden data) inside the
  company's own walls, or in a **TEE**. A TEE (Trusted Execution Environment) is a
  locked safe room inside a computer where code runs and even the room's owner
  cannot peek inside.

**The build step** turns the worker's source code into something runnable. If the
worker wrote Java, we compile it with `javac` (the standard Java compiler). If they
wrote Rust, we compile it with `rustc`. "Pinned" means we fix the exact tool
version so the build comes out the same every time.

**Trust boundaries** are the lines where the level of trust changes:

- **TB1 (worker ↔ platform).** The worker's code is untrusted. It runs in a
  **sandbox** — a safe box with no internet, no writing to disk, and a strict list
  of allowed actions.
- **TB2 (platform ↔ company).** Only *results* cross this line: a signed verdict
  (pass/fail) and a few numbers. The real test data never leaves the company.
- **TB3 (eval ↔ production).** Before a winning part goes into the real product,
  the company rebuilds and re-checks it on its own machine. The exact binary is
  pinned by a **hash** (a fingerprint that changes if even one byte changes).

**Components, listed plainly:**

1. Contract Registry — the list of job cards, with versions and rules.
2. Task Documents — the human-readable requirements plus a machine-readable
   checklist the worker must accept.
3. Worker Workbench — the worker's tools; Java or Rust.
4. Build Service — runs `javac` or `rustc` and produces a fingerprintable result.
5. Synthetic Data Service — realistic sample data, never the real data.
6. Eval Fabric — the federated build-and-test machines.
7. Scoring & Leaderboard — the gate and the rankings.
8. Identity/Reputation — who is who, and how much to trust them.
9. Payments/Escrow — safe money handling with a dispute window.
10. Audit Ledger — an append-only history, so nothing can be quietly changed.
11. Integration SDK — the company's toolkit to pull a winner back in.

---

## 5. The job card: contract + task document

A **contract** is the machine-readable job card. Here is one, with plain-English
notes on each line:

```
contract module.image.resize {
  version: 1.4.0
  task_document: ref:docs/tasks/image-resize.md    // the instructions to accept first
  language: java | rust        // worker chooses; must match the existing module
  signature: (in: Image, w: u32, h: u32, filter: enum) -> Image
  license: company-proprietary / contributor-assignment
  disclosure_tier: L1            // how much we reveal (see below)
  invariants: [ dims_match, alpha_preserved, channel_range_preserved ]
  resource_budget: { time_ms: 12, peak_mem_mb: 8, artifact_kb: 64 }
  oracle: { properties: ref, fuzzers: ref, holdout: hidden, differential: incumbent }
  scoring: { gate: all_pass, rank: pareto([time, peak_mem]), tiebreak: [artifact_kb, complexity] }
  forbidden: [ net, fs_write, threads>1, nondeterminism ]
  acceptance: worker_must_accept_task_document
}
```

- **version** — job cards change over time; this is which version.
- **task_document** — the plain-language instructions (see below).
- **language** — Java or Rust. The worker picks to match the existing module.
- **signature** — the shape: what goes in, what comes out. For an image-resize
  module: take an image, a width, a height, and a filter choice; give back an image.
- **license** — what rights the worker gives the company in exchange for pay.
- **disclosure_tier** — how much the company is willing to reveal (see below).
- **invariants** — always-true rules the result must follow (for example: the
  output size must match the requested size; the see-through part of an image must
  stay see-through).
- **resource_budget** — the limits: time, memory, and how big the finished file
  may be.
- **oracle** — how we decide correctness. "Hidden" holdout means secret questions.
  "Differential vs incumbent" means we compare the new part's behavior against the
  old working part on the same random inputs.
- **scoring** — "all_pass" gate, then rank by a **Pareto** comparison (explained in
  section 6).
- **forbidden** — things the code must not do: use the network, write files, spawn
  many threads, or behave differently each run.
- **acceptance** — a reminder that the worker must accept the task document.

### The task document

The **task document** is the human-readable version plus a machine-readable
checklist. It says what the module must do and when it is done.

```
requirements:
  - "resize honors (w,h) exactly"
  - "alpha channel preserved"
  - "no network / filesystem / threads"
done_criteria: "all hidden property + holdout tests pass; artifact within budget"
runtime: java | rust
```

**Consent matters.** A worker must explicitly accept the task document before
their submission is even considered. This is recorded as a signed event. No
acceptance, no entry — like signing a job form before you start.

### How much the company reveals (disclosure tiers)

- **L0** — only the shape and words. Cheapest to publish, hardest to help with.
- **L1** — also a few made-up samples and a self-test kit. The **default**.
- **L2** — also some redacted real examples. Best contributions, but the highest
  risk of leaking something. Use carefully.

---

## 6. How we check and rank the work

### Step 1: Build (before any judging)

We compile the source with the exact, fixed tool version. Hoping the same source
builds to the same result every time is not good enough — we *check* it and
fingerprint the result. A build that fails is rejected immediately. (We never pay
for code that does not build.)

### Step 2: The gate (pass or fail)

The **gate** is a series of checkpoints. A submission must pass all of them to
count. They run in order, and the first failure stops the line:

1. **Builds the same way twice** with the fixed tool version.
2. **Passes the hidden property tests** — checks that describe what must *always*
   be true.
3. **Passes fuzzing** — we throw lots of random, odd inputs at it, plus secret
   exam questions (the "holdout").
4. **Matches the old code (differential)** on held-back inputs. This catches "new
   code that is subtly different in the wrong way."
5. **Matches the company's goal** — the finished build does what the company's
   checklist said it must do.
6. **Breaks no forbidden rules**, and stays inside the time and memory limits.

If a submission fails, the worker gets a **sanitized** explanation — enough to
learn from, but containing none of the secret data.

### Step 3: Ranking (Pareto)

Among the submissions that pass the gate, we compare speed and memory. We use a
**Pareto** comparison, which is easier to understand with athletes:

- Runner A finishes in 10 seconds but drinks 2 bottles of water.
- Runner B finishes in 12 seconds but drinks 1 bottle.
- Neither is better than the other in *every* way. **Both stay in the race.**
- Runner C finishes in 14 seconds and drinks 3 bottles. C is worse on *everything*,
  so C drops out.

The parts that remain are the **Pareto frontier**. Ties are broken by file size,
then by how simple the code is, then by a human look.

The numbers we look at are: median speed (p50), worst-case speed (p99), peak
memory, file size, and an energy estimate.

**Anti-cheating (important):** the hidden questions change each round; we use
"trick" inputs outside the normal pattern (**OOD canaries**); we always compare
against the old code; and we never trust numbers a worker reports about themselves.
This defends against the "when a measure becomes a target, it stops being a good
measure" trap.

---

## 7. How workers get paid and trusted (marketplace)

- **Escrow:** when a contract is published, the money is set aside with a trusted
  neutral party. It is released when the winning part is **merged** (added to the
  company's real system), not merely when it tops the leaderboard.
- **The worker accepts the task document first**, then submits. Both events are
  recorded.
- **Reputation** grows from parts that were actually merged and used in
  production — not from raw submissions. One person pretending to be many
  accounts ("Sybil") is blocked by identity limits and caps on new accounts.
- **Disputes:** there is a waiting period. A disputed result can be re-checked on a
  second, independent runner.

---

## 8. Safety, secret-keeping, and legal

- **Clean-room promise:** workers build only from the published task document. The
  audit ledger proves no one peeked at the secret source.
- **Rights:** when a worker accepts the payment, rights are transferred in a clear,
  automatic way. For proprietary contracts the default is "the company gets the
  code" (contributor assignment).
- **Secret tests stay home:** they run inside the company's own machine or a TEE,
  so they never travel across the platform.
- **Supply chain:** every artifact is signed, listed in a bill of materials
  (SBOM — a list of what is inside), built reproducibly, and re-checked before merge.
- **Legal:** liability and indemnity terms, and which country's law applies, are
  set per contract.

---

## 9. From a submission to the real product

```
accept task doc → submit → compile → gate → pareto rank → human review gate
→ escrow release → signed binary → company-node re-verification → merge behind flag
→ production telemetry → maintenance contract (optional)
```

In plain terms: the worker agrees, submits, their code is built and checked and
ranked, a human gives a final look, the money is released, the exact signed build
is re-checked on the company's machine, added to the real product *behind a flag*
(so it can be turned off quickly if something is wrong), watched in production, and
optionally kept healthy with a maintenance deal.

The reason we stress this whole pipeline: in real life, the *integration* work
(adding a part into a big system and keeping it healthy) is often more expensive
than writing the part. So the first version must *measure* that cost and beat it —
we are not allowed to just assume it is cheaper.

---

## 10. The things we store (data model)

Each of these is a "thing" the system remembers:

- **Company** — the organization asking for work.
- **Contract** — a versioned job card.
- **TaskDocument** — the instructions a worker accepts.
- **Acceptance** — the signed record that a specific worker accepted a specific
  task document.
- **DisclosureTier** — how much the company reveals (L0/L1/L2).
- **Submission** — the source code, its language, its fingerprint, its signature,
  and which acceptance it belongs to.
- **Build** — the tool version used and the fingerprint of the built result.
- **EvaluationRun** — one judging run: the verdict, the numbers, the random seed.
- **LeaderboardEntry** — a ranked position.
- **Bounty** — the reward offered for a contract.
- **Escrow** — the held money and its state.
- **Reputation** — a worker's track record.
- **AuditEvent** — one recorded event in the history.
- **MergeRecord** — proof that a part was added to the real product.

---

## 11. The buttons and doors (API surface)

This is the list of "requests" the website can handle. ("API" just means the set
of ways one program asks another program to do something.)

```
POST /contracts                       publish a contract + task document
GET  /contracts/{id}/workbench        fetch the public spec + sample data
POST /contracts/{id}/accept           a worker accepts the task document (signed)
POST /submissions                     upload source {language, contractId, acceptanceId}
GET  /submissions/{id}/build          build status + artifact fingerprint
GET  /submissions/{id}/verdict        gate result + Pareto numbers (sanitized)
GET  /contracts/{id}/leaderboard      the rankings
POST /bounties/{id}/award             release the reward
POST /integrations/{id}/merge         add the winner to the real product
```

---

## 12. What can go wrong, and when to stop

| What can go wrong | How we reduce the risk | The signal that we should stop |
|---|---|---|
| Hidden tests are incomplete, so bad code wins | compare against the old code; trick inputs | more bugs slip through than if we built it in-house |
| A secret leaks through the shape or the sample data | disclosure tiers; TEE | any real leak happens |
| People game the scores | rotating secret questions; Pareto | scores go up but the product does not improve |
| Integration costs more than it saves | toolkit; feature flags; re-checking | the total cost is the same or higher than building in-house |
| Java is hard to run safely in the sandbox | run it in a locked-down container (see IMPLEMENTATION) | a sandbox escape or an untrustworthy measurement |
| Fake accounts abuse trust | reputation; caps; escrow | any payout fraud |
| Too few modules are verifiable | an eligibility filter | fewer than a set number of contracts per quarter |

**The big stop rule:** if the total cost — reward + integration + upkeep — per
*actually merged* part does not beat building it in-house within two quarters, then
the whole proprietary idea is proven wrong. We stop.

---

## 13. The build order (roadmap)

Each step has a clear "we are done when" test.

1. **P0 Framing (1–2 weeks).** Pick one kind of module (for example, image
   resizing) and the language the existing code uses. *Done when:* we can name the
   module and the language and have a working old version to compare against.
2. **P1 Contract & task document (2–4 weeks).** Build one real contract and task
   document, with the acceptance flow. *Done when:* ten people can read it and
   correctly predict what will pass and what will fail, and acceptance is enforced.
3. **P2 Eval Fabric (4–8 weeks).** The build service for both Java and Rust, the
   sandboxes, the checkpoints (properties, fuzzing, comparison, hidden questions),
   and the measurements. *This is the hardest 80% of the work.* *Done when:*
   results repeat exactly, there are no side doors, and both languages work.
4. **P3 Trust & IP (3–5 weeks).** The locked safe room (TEE) or on-premise node,
   signing, the audit ledger, and the rights flow. *Done when:* the secret tests
   never leave the company's machine.
5. **P4 Marketplace (3–5 weeks).** Reward, escrow, reputation, disputes. *Done
   when:* money can move safely.
6. **P5 Integration (ongoing).** The toolkit, the human review gate, merging, and
   rollback. *Done when:* one crowd-built part is running in production.
7. **P6 Scale.** Generalize to more kinds of modules. Add AI help for *writing the
   task documents and tests* — not for secretly writing the answers.

---

## 14. The smallest first version (MVP)

Start tiny: one company, one kind of module, one evaluator machine, sample data at
tier L1, a leaderboard, and **no money at all**. The contract format and the build
service support both Java and Rust from day one, but the first proof may launch with
only the language the existing code uses.

The goal of the MVP is narrow: prove that **crowd-built code can be verified and
can beat the old code**. Do that before adding locked rooms (TEEs) or payments.

---

## 15. Things we have not decided yet

- Which language the first module uses (Java or Rust).
- The Java sandbox path — a locked-down container, or the discouraged Java-to-WASM
  route (see `docs/IMPLEMENTATION.md` for why the container is preferred).
- Whether to use a TEE now or an on-premise node later.
- Who writes the task documents and hidden tests — the company's engineers or a
  platform team.
- The exact rule for "verifiable enough" to be allowed in.
