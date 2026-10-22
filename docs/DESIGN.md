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
**task document** (plain-language instructions). An outside **worker** uses the
language the company's **task committee** says its existing software is written in
— Java, C, Go, Python, TypeScript, or several at once — accepts the task document,
and submits source code. The platform builds and tests that code privately.
Correct submissions are ranked by speed and memory. The best one is paid and added
to the company's system.

There is also a second kind of work, described in
[`NEW-BUILDS.md`](NEW-BUILDS.md): a **brand-new** app with no existing code, where
a worker proposes a **design, a plan, and a rate of pay**, and the work is done in
**milestones**.

In technology terms, Symmetry is a **Java Spring Boot control plane**, a **React**
console, a **Python AI service**, a **publisher-hosted runner appliance**
(AlmaLinux + KVM microVMs), and a **Rust→WASM module SDK**, sitting on
**PostgreSQL** with **MinIO + Redis**. The choices and reasons are in
[`STACK.md`](STACK.md); the runner and licensing decisions are in the
[ADR index](adr/).

**Target:** proprietary (closed, company-owned) software.
**Two kinds of work:** `optimize` an existing module, or `build` a new app.
**How we measure success:** correctness first, then speed and memory (for
`optimize`); a quality rubric and milestone reviews (for `build`).

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
2. **The language follows the task.** For `optimize` work, the language is whatever
   the task-submitting committee says the existing software uses. For `build` work,
   the worker proposes the language and the committee approves it. Adding a new
   language should mean plugging in a new compiler, never editing the center.
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
9. **Untrusted code runs in a KVM microVM.** The JVM, Chicory and Python tiers are
   inner layers. A plain container is not the host boundary. If `/dev/kvm` is
   missing, the runner refuses to start; it does not fall back to Docker.
10. **Every update and result is signed.** The publisher appliance pulls signed
    updates (online or as an offline bundle), and the runner signs its results.
    Open-source code does not prove that a downloaded binary is genuine.

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
     │ language per task    │  │                  │   │                    │
     └──────────┬──────────┘  └──────────────────┘   └─────────┬──────────┘
                │ submit signed source + accepted task doc      │ pull winner
                ▼                                               ▼
        ┌──────────────────────── Eval Fabric (federated) ────────────────────────┐
        │  Build: javac (JDK 21) │ rustc (pinned) │ CPython (pinned)              │
        │  Publisher Appliance (AlmaLinux + KVM microVM)  │  Neutral Evaluator    │
        │  · JVM / Chicory / Python tiers                 │  (K8s + Kata, our     │
        │  · no network · signed result                   │   bare metal)         │
        │  · unit/property/fuzz · differential            │  · hidden holdout      │
        └───────────────────────────┬──────────────────────────────────────────────┘
                                     ▼  signed verdict + Pareto metrics (tests never leave)
                       Compile → Gate → AI adjudication → Leaderboard → Escrow
```

**The Platform Control Plane** is the main website. It holds a **registry**
(the list of published contracts), the **task documents**, **identity and
reputation** (who a worker is and how trusted), the **leaderboard** (rankings),
**escrow** (money held safely until a job is done), and an **audit ledger** (a
tamper-evident history of events). We call it a "control plane" because it does not
do the heavy work itself — it *coordinates*.

**The Worker Workbench** is the tool a worker uses to do the job: a command-line
tool and a web editor. It can fetch the public part of the contract and some
made-up sample data, but never the secrets. It supports whichever language the task
calls for, pulling the right compiler from the **toolchain registry**.

**The Synthetic Data Service** makes fake-but-realistic sample data. Think of
"practice questions" that feel like the real exam but are not the real exam.

**The Integration SDK** is a small toolkit the company runs on its own codebase.
It helps them pull a winning module back into their system.

**The Eval Fabric** is the group of machines that build and test submissions. It
is **federated**, meaning it can run in more than one place at once:
- **Publisher Appliance** runs the *public* checks (build it, run simple tests,
  compare against the old code, measure speed/memory) inside KVM microVMs on the
  publisher's hardware.
- **Neutral Evaluator** reruns payment-critical checks on Kubernetes + Kata on
  hardware Symmetry controls, independent of the publisher's host.
- **Company Eval Node** runs the *secret* checks (the real hidden data) inside the
  company's own walls, or in a **TEE**. A TEE (Trusted Execution Environment) is a
  locked safe room inside a computer where code runs and even the room's owner
  cannot peek inside.

**The build step** turns the worker's source code into something runnable. We pick
the compiler based on the language the task declares, from a **toolchain registry**
(Java, Rust, C, Go, Python, TypeScript, and so on — added over time). "Pinned" means
we fix the exact tool version so the build comes out the same every time.

**Trust boundaries** are the lines where the level of trust changes:

- **TB1 (worker ↔ platform).** The worker's code is untrusted. It runs inside a
  **KVM microVM** (Firecracker or Kata) with no internet, a read-only root, and
  host-enforced limits. The JVM, Chicory and Python tiers are inner layers. A
  plain container is not the boundary; a chroot is not a security boundary.
- **TB2 (platform ↔ company).** Only *results* cross this line: a signed verdict
  (pass/fail) and a few numbers. The real test data never leaves the company.
- **TB3 (eval ↔ production).** Before a winning part goes into the real product,
  the company rebuilds and re-checks it on its own machine. The exact binary is
  pinned by a **hash** (a fingerprint that changes if even one byte changes).

**Components, listed plainly:**

1. Contract Registry — the list of job cards, with versions and rules.
2. Task Documents — the human-readable requirements plus a machine-readable
   checklist the worker must accept.
3. Worker Workbench — the worker's tools; language per task.
4. Build Service — compiles or interprets the source via the toolchain registry and
   produces a fingerprintable result.
5. Synthetic Data Service — realistic sample data, never the real data.
6. Eval Fabric — the federated build-and-test machines.
7. Scoring & Leaderboard — the gate and the rankings.
8. Identity/Reputation — who is who, and how much to trust them.
9. Payments/Escrow — safe money handling, AI adjudication and one human appeal.
10. Audit Ledger — an append-only history, so nothing can be quietly changed.
11. Integration SDK — the company's toolkit to pull a winner back in.
12. Runner Registry — registers publisher appliances, leases jobs and verifies
    signed results.
13. Publisher Appliance — the AlmaLinux image with KVM microVMs and signed updates
    that runs on the publisher's hardware.
14. AI Adjudicator — decides payment from structured evidence; one human appeal.
15. Neutral Evaluator — Kubernetes + Kata/Firecracker on hardware we control for
    payment-critical reruns.

The concrete tools each component is built with (Java/Spring, React/custom design system,
Python/FastAPI, Rust/WASM, PostgreSQL, AlmaLinux/KVM) are listed in [`STACK.md`](STACK.md).

---

## 5. The job card: contract + task document

A **contract** is the machine-readable job card. Here is one, with plain-English
notes on each line:

```
contract module.image.resize {
  version: 1.4.0
  task_kind: optimize | build   // existing module, or a brand-new app
  work_kind: compute            // V1 uses compute; service/ui/data/system reserved
  task_document: ref:docs/tasks/image-resize.md    // the instructions to accept first
  language: <any>              // for `optimize`: set by the committee from the
                              // existing software; for `build`: proposed by the worker
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
- **task_kind** — `optimize` (improve existing code) or `build` (make a new app).
  The rest of this section describes `optimize`; see `NEW-BUILDS.md` for `build`.
- **task_document** — the plain-language instructions (see below).
- **language** — for `optimize`, set by the committee from the existing software
  (any language, not just Java or Rust); for `build`, proposed by the worker and
  approved by the committee.
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
runtime: <language set by the committee>
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

### For `build` work (new apps)

There is no old code to compare against, so the answer key is **written from the
requirements** instead. The brief's **acceptance criteria** become the tests, and
correctness is joined by a **quality rubric** (security, maintainability,
licensing, deployability) and **milestone reviews** by people. This is covered in
full in [`NEW-BUILDS.md`](NEW-BUILDS.md).

---

## 7. How workers get paid and trusted (marketplace)

- **Escrow:** when a contract is published, the money is set aside with a trusted
  neutral party. It is released only after the correctness gate, the AI payment
  decision and the appeal window.
- **AI-final:** the AI adjudicator (independent of the publisher and worker) makes
  the first payment decision from a structured evidence bundle. It is final unless
  the worker uses the one appeal.
- **One human appeal:** a worker may appeal once with evidence. A human reviewer
  sees the same evidence and the AI's reasons, then upholds, reverses or remands.
  There is no human review of every payment.
- **The worker accepts the task document first**, then submits. Both events are
  recorded.
- **Reputation** grows from parts that were actually merged and used in
  production — not from raw submissions. One person pretending to be many
  accounts ("Sybil") is blocked by identity limits and caps on new accounts.
- **Disputes:** payment-critical results can be re-run on the neutral evaluator.
  The decision log records the model version, policy and evidence hashes.
- **New-build pay is different.** For `build` work, pay is hourly and/or fixed-price
  per **milestone**, released as milestones pass — never a blank check for hours.
  The full model (proposals, rates, quality bar, anti-padding rules) is in
  [`NEW-BUILDS.md`](NEW-BUILDS.md).

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
  Dependencies are checked against an allowed-license list, so a copyleft or
  forbidden license cannot slip in unnoticed.
- **Signed updates:** the publisher appliance pulls signed OTA metadata over
  outbound mTLS. Air-gapped sites use the same signed bundle. Open-source code
  does not authenticate a downloaded binary; the signature does.
- **AI payment policy:** the contract states that an AI makes the first decision,
  the appeal window, the evidence it sees and the liability cap. Model name,
  version, prompt hash and output hash are stored with the decision.
- **Licensing:** the runner agent and SDK are Apache-2.0; the control plane and AI
  service are AGPL-3.0; support, updates, signing keys and holdouts are commercial
  or private. See [ADR-0002](adr/0002-licensing.md).
- **Legal:** liability and indemnity terms, and which country's law applies, are
  set per contract. Automated payment decisions may create a right to explanation
  or human review in some jurisdictions; the one-appeal path is the answer.

---

## 9. From a submission to the real product

```
accept task doc → submit → compile → gate → pareto rank → AI adjudication
→ one human appeal window → escrow release → signed binary → neutral re-verification
→ merge behind flag → production telemetry → maintenance contract (optional)
```

In plain terms: the worker agrees, submits, their code is built and checked and
ranked. The AI adjudicator makes the first payment decision from the evidence.
The worker gets one appeal if they disagree. The money is released from escrow,
the exact signed build is re-checked on the company's machine or the neutral
evaluator, added to the real product *behind a flag* (so it can be turned off
quickly if something is wrong), watched in production, and optionally kept healthy
with a maintenance deal.

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
- **Runner** — a registered publisher appliance: host details, capabilities,
  public key, version and health.
- **JobLease** — the deadline-bound permission for one runner to work on one job.
- **SignedResult** — the runner's signed verdict, logs digest, metrics and artifact
  digest.
- **AdjudicationDecision** — the AI's payment decision, evidence hashes, model and
  policy version.
- **Appeal** — one worker appeal with evidence, reviewer and outcome.
- **UpdateRelease** — a signed appliance version, manifest, SBOM and rollback
  target.
- **LeaderboardEntry** — a ranked position.
- **Bounty** — the reward offered for a contract.
- **Escrow** — the held money and its state.
- **Reputation** — a worker's track record.
- **AuditEvent** — one recorded event in the history.
- **MergeRecord** — proof that a part was added to the real product.

The `build` track stores extra things — **Brief**, **Proposal**, **ReviewScore**,
**Milestone**, **RateCard**, **WorkLogEntry**, **QualityReport**, and **Handover**.
They are listed in [`NEW-BUILDS.md`](NEW-BUILDS.md).

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
POST /runners/register                join a publisher appliance with a token
POST /runners/{id}/heartbeat          report health and capabilities
POST /runners/{id}/lease              lease one job (deadline + nonce)
POST /runners/{id}/results            submit a signed result
POST /submissions/{id}/adjudicate     ask the AI adjudicator for a payment decision
POST /payments/{id}/appeal            open the one evidence-based human appeal
GET  /updates/{channel}/manifest      signed update metadata for the appliance
```

The `build` track adds more doors (proposals, milestones, quality reports,
handover). They are listed in [`NEW-BUILDS.md`](NEW-BUILDS.md).

---

## 12. What can go wrong, and when to stop

| What can go wrong | How we reduce the risk | The signal that we should stop |
|---|---|---|
| Hidden tests are incomplete, so bad code wins | compare against the old code; trick inputs | more bugs slip through than if we built it in-house |
| A secret leaks through the shape or the sample data | disclosure tiers; TEE | any real leak happens |
| People game the scores | rotating secret questions; Pareto | scores go up but the product does not improve |
| Integration costs more than it saves | toolkit; feature flags; re-checking | the total cost is the same or higher than building in-house |
| A language is hard to run safely in the sandbox | run it inside the KVM microVM; add a language to the registry only once it can run safely | a microVM escape or an untrustworthy measurement |
| The publisher host cannot expose `/dev/kvm` | preflight fails; use bare metal or the neutral evaluator; never fall back to Docker | a runner silently runs with a weaker boundary |
| A cloud instance has no nested virtualisation | certify instance types; test `/dev/kvm` at boot | a “supported” cloud host that cannot run jobs |
| The AI denies a valid payment | escrow; evidence bundle; one human appeal; liability cap | a pattern of reversals or unexplained denials |
| An update mirror is compromised | signed metadata; anti-rollback; offline verification; key custody | a publisher applies an unsigned or rolled-back image |
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
3. **P2 Eval Fabric (4–8 weeks).** The build service (starting with Java, Rust and Python)
   via the toolchain registry, the KVM microVM and its inner runtime tiers, the
   checkpoints (properties, fuzzing, comparison, hidden questions), and the
   measurements. *This is the hardest 80% of the work.* *Done when:* results repeat
   exactly, there are no side doors, and the first languages work.
   - **P2b Publisher appliance (2–4 weeks).** qcow2/OVA image, preflight, join
     token, signed OTA and A/B rollback. *Done when:* a publisher imports the
     image, passes preflight and runs a signed job. See ADRs 0001, 0004 and 0006.
4. **P3 Trust & IP (3–5 weeks).** The locked safe room (TEE) or on-premise node,
   signing, the audit ledger, and the rights flow. *Done when:* the secret tests
   never leave the company's machine.
5. **P4 Marketplace (3–5 weeks).** Reward, escrow, AI adjudication, one human
   appeal, reputation and disputes. *Done when:* money can move safely and every
   decision can be replayed.
6. **P5 Integration (ongoing).** The toolkit, neutral re-verification, merging and
   rollback. *Done when:* one crowd-built part is running in production.
7. **P6 Proposals & review (the `build` track).** Briefs, proposals (design + plan
   + rate), rubric scoring, and an optional design bake-off.
8. **P7 Milestones & quality gate (the `build` track).** Milestone escrow,
   automated quality scans (tests, security, dependency/license), human review, and
   handover.
9. **P8 Scale.** Generalize to more languages and kinds of modules. Add AI help for
   *writing the task documents and tests* — not for secretly writing the answers.

---

## 14. The smallest first version (MVP)

Start tiny: one company, one kind of module, one KVM runner, sample data at
tier L1, a leaderboard, and **no money at all**. The contract format and the build
service are built to support **any language** through the toolchain registry, but
the first proof may launch with only the language the existing code uses (Java,
Rust and Python are the first three runtime entries). The `build` track comes
later.

The goal of the MVP is narrow: prove that **crowd-built code can be verified and
can beat the old code**. Do that before adding locked rooms (TEEs) or payments.

---

## 15. Things we have not decided yet

- Which language the first module uses (whichever the existing software is written
  in; Java, Rust and Python are the first three runtime entries).
- When to add the `build` track (proposals, rates, milestones) — see P6–P7 above.
- Which cloud instance types are certified for nested virtualisation in AWS and
  GCP (see [ADR-0006](adr/0006-cloud-appliance.md)).
- Which escrow provider, liability cap and appeal window apply to AI-final
  payments (see [ADR-0003](adr/0003-ai-adjudication.md)).
- Whether the AI adjudicator stays on the same model or moves to an independent
  model once volume justifies it.
- Whether to add native Rust as a separate toolchain tier alongside `wasm32`.
- Whether to use a TEE now or an on-premise node later.
- Who writes the task documents and hidden tests — the company's engineers or a
  platform team.
- The exact rule for "verifiable enough" to be allowed in.
- Which licence applies to `docs/` (see [LICENSING.md](../LICENSING.md)).
- For `build` work: the default pay shape, rubric weights, and how strict the
  proposal trust tiers are (see `docs/NEW-BUILDS.md`).
