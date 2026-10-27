# Symmetry: Future of software tech work — Building New Apps from Scratch

The other docs describe improving an **existing** module: the shape is known, the
old code works, and we compare the new code against it.

This doc covers the harder case: a company has a **completely new idea** and no
old code. There is nothing to compare against. Workers must **design** the software
and **plan** how to build it, and they set a **rate of pay**. We must get good,
acceptable-quality software out of this, safely, for the organization paying.

It is written plainly. Every special word is explained.

---

## 1. Two kinds of work

Symmetry handles two `task_kind`s. The job card says which one it is.

| | **`optimize`** (existing module) | **`build`** (new app / new idea) |
|---|---|---|
| Who decides the language? | The **task committee** states the language(s) the existing software uses. | The **worker proposes** the language and stack; the committee approves. |
| What the worker sends first | The finished module (source code). | A **proposal**: a design + an implementation plan + a rate of pay. |
| How we judge correctness | Compare against the old code + hidden tests. | Against **acceptance tests written from the brief** + review. |
| How we rank | Pareto on speed and memory. | A **quality rubric** + proposal scoring, then milestone reviews. |
| How pay works | Bounty released on merge. | Milestone escrow, hourly or fixed-price (see section 7). |

The **first version** of Symmetry only does `optimize`. `build` is a later stage
(see the roadmap), but we design for it now so the center is not rigid.

There is also a small **exercise authoring** track for practice-sized tasks:
import a public Java file, remove a method body, add public and hidden tests, and
publish it. It stores submissions and runs static checks only — it does not
execute, judge or pay. It is a separate bounded context, not a `build` proposal or
milestone flow; see [`adr/0007-exercise-authoring-track.md`](adr/0007-exercise-authoring-track.md).

---

## 2. Languages are not limited to Java, Rust and Python

The earlier docs said "Java or Rust." That was wrong as a rule. Java, Rust and
Python are the first three supported runtimes, not the limit. The correct rule:

- **Existing software:** whatever language(s) the **task-submitting committee**
  declares. It might be Java, C, C++, Go, Python, TypeScript, Kotlin, or several at
  once. The organization already runs it, so the language is fixed by reality.
- **New software:** the **worker proposes** the language and stack, and the
  committee approves it — within a list of stacks the platform can safely build and
  run, and within the company's own allowed-stack rules (for example, "we only
  deploy on the JVM" or "no GPL-licensed dependencies").

**What this means for the platform:** adding a language must be "plug in a new
compiler," not "edit the center." We call that list-of-compilers a **toolchain
registry**. Java, Rust and Python are just the **first three** entries.

---

## 3. The greenfield problem (why this is hard)

When we improve an existing module, the hidden tests know the right answers,
because the old code already works. For a **new** idea, nobody has the answers yet.
So the "answer key" must be **built from the requirements**, and a human must judge
whether the design is sound. This section explains how we make that reliable.

Three problems, and our answers:

1. **No right answers to test against** → the committee writes a **brief** with
   clear, checkable **acceptance criteria** (section 4). Those become the answer key.
2. **Design quality is not a simple pass/fail** → proposals and milestones go
   through a **review rubric** scored by people (section 5 and 8).
3. **Hourly pay can be gamed (padding hours)** → pay is tied to **milestones**, not
   to raw hours (section 7).

---

## 4. Phase 1 — The brief (the job card for a new app)

The committee publishes a task document that is **rich enough to design against**
but still protects secrets. It contains:

- **The problem** — what the app must do, in plain words, and who uses it.
- **Must-have requirements** — a numbered list. Each must be *checkable* ("a user
  can reset a password by email"), not vague ("the app should be nice").
- **Non-functional requirements (NFRs)** — the quality targets:
  - performance ("a page loads in under 2 seconds for 100 users"),
  - security ("passwords stored with a modern hash; no secrets in logs"),
  - accessibility ("works with a keyboard and a screen reader"),
  - reliability ("survives restart without losing data").
- **Constraints** — allowed languages/stacks, required cloud or on-prem, license
  rules, data-privacy rules, deadline, and budget.
- **What "done" means** — the acceptance checklist the work will be judged against.
- **Budget and pay shape** — total not-to-exceed budget, and whether pay is hourly,
  fixed-price, or a mix.

**Consent:** just like the `optimize` track, a worker must **accept the brief**
before a proposal counts.

---

## 5. Phase 2 — Proposals (design + plan + rate)

A vetted, senior worker (see section 9) sends a **proposal** containing three parts:

1. **Design** — the architecture: the main parts, how they talk to each other, the
   data model, the key decisions and the trade-offs, and what is deliberately left
   out.
2. **Implementation plan** — the stack (languages, frameworks), the **milestones**
   with what each delivers, the test strategy, the risks, and the estimated effort.
3. **Rate** — the worker's **hourly rate** and/or a **fixed price per milestone**,
   with a realistic estimate and a not-to-exceed cap.

### How we judge a proposal (rubric)

Reviewers (the company plus senior platform reviewers) score each proposal:

| Criterion | The question |
|---|---|
| Requirements coverage | Does the design address every must-have requirement? |
| Architecture soundness | Are the parts sensible and the boundaries clean? |
| Security & privacy | Are the obvious dangers handled from the start? |
| Testability | Can correctness actually be checked? |
| Maintainability | Will this be understandable and changeable later? |
| Risk & realism | Are the risks named, and is the estimate believable? |
| Cost | Is the rate and estimate fair for the value? |

### Design bake-off (optional)

If several strong workers apply, the committee may pay a small **design bounty**
to the top two or three and pick a winner. This is like an audition: a little money
buys a real design instead of a guess.

**The decision is the organization's to make.** Symmetry's job is to give them a
clear, comparable scorecard so the choice is informed, not a coin flip.

---

## 6. Phase 3 — Building in milestones (with review gates)

The chosen worker builds in **milestones**. A milestone is a small, agreed chunk
with its own acceptance check. Escrow money is released **per milestone** when it
passes review — never all at once up front.

A typical milestone path for a new app:

1. **Skeleton** — the project builds, runs, and deploys; core structure in place.
2. **Core features** — the must-have requirements work, with tests.
3. **Hardening** — security, performance, accessibility, error handling.
4. **Handover** — documentation, a runbook, and a walkthrough.

Between milestones, the company **reviews** the work: it meets the acceptance
check, satisfies the quality bar (section 8), and stays within the plan. If a
milestone fails, the money is not released, and the plan is adjusted.

---

## 7. Paying fairly (and safely)

Hourly pay is easy to abuse if unattached to results. Our rules:

- **Pay is tied to outcomes, and hours must be evidenced.** Time is tracked with a
  work log plus real evidence (commits, pull requests). Reviewers sanity-check that
  the hours match the progress.
- **Escrow and a not-to-exceed cap.** The company funds a maximum. We never pay
  beyond it without a new, explicitly approved agreement.
- **Per-milestone release.** Money moves when a milestone passes, so a stalled or
  poor build cannot drain the budget.
- **Choose the shape.** Fixed-price per milestone (worker carries risk), hourly
  (company carries risk but with caps and evidence), or a mix: hourly for design,
  fixed-price for build.
- **Dispute window.** Either side can dispute; a neutral reviewer re-examines the
  evidence before money moves.
- **Reputation from delivered milestones**, not from hours billed.

---

## 8. The quality bar for new software (Definition of Done)

For a brand-new app there is no old code to be "as good as," so we define quality
explicitly. A milestone is "done" only when it passes this bar:

- **Correctness** — every acceptance criterion it claims is covered by a passing
  test.
- **Security** — automatic scans find no critical issues: secret scanning,
  dependency vulnerability scanning, and static analysis (SAST). No secrets in code.
- **Dependency and license hygiene** — a bill of materials (SBOM) exists, and every
  dependency's license is on the company's allowed list.
- **Maintainability** — code style checks pass; tests cover the important paths; no
  giant copy-pasted blocks.
- **Performance** — meets the NFR budgets if the brief set any.
- **Deployability** — a reproducible build, a container or install steps, config
  that is not hard-coded, and a way to run and stop it.
- **Handover** — a README, an architecture sketch, and a short runbook so someone
  else can operate it.

An automated **quality gate** checks what a computer can check (tests, scans,
style). A **human review** checks the rest (design, clarity, fit). Both must pass.

---

## 9. Vetting senior workers

Designing a new app is a higher-trust job than optimizing a tiny function. So the
`build` track is **gated by trust tiers**:

- **Proposal rights** — only workers with a verified identity, a portfolio, and a
  track record (past merged work) may propose. New accounts start on `optimize`
  tasks and earn their way up.
- **References and review history** — past design quality feeds the worker's
  reputation for design, separate from raw coding reputation.
- **Financial and legal checks** — because real money and real contracts are
  involved, identity and payout details are verified.

This is the same "trust tier" idea used elsewhere, applied more strictly.

---

## 10. How the organization makes an acceptable decision

The committee should be able to decide with confidence. Symmetry gives them:

1. **Comparable proposals** — every proposal answers the same brief and is scored on
   the same rubric, laid side by side.
2. **A scorecard** — criterion-by-criterion scores, with the reviewers' notes.
3. **A clear cost picture** — rate, estimate, cap, and pay shape, all visible.
4. **A staged commitment** — they can start with one milestone and stop if they are
   unhappy, instead of betting the whole budget.
5. **Escape hatches** — feature flags, a dispute window, and re-review before merge.

The committee always makes the final call. Symmetry's role is to make the trade-offs
**visible and honest**, not to decide for them.

---

## 11. New things we store (data model additions)

On top of the existing data model in `docs/DESIGN.md`:

- **Brief** — the requirements, NFRs, constraints, budget, and acceptance checklist
  for a new app (a richer `TaskDocument`).
- **Proposal** — a design + plan + rate submitted by a worker for a `build` task.
- **ReviewScore** — a rubric score plus notes from one reviewer on a proposal or a
  milestone.
- **Milestone** — an agreed chunk of work with its own acceptance check and price.
- **RateCard** — the worker's hourly rate and/or per-milestone prices and cap.
- **WorkLogEntry** — an evidenced record of time (paired with commits/PRs).
- **QualityReport** — the automated scan results (tests, SAST, dependencies,
  licenses) for a milestone.
- **Handover** — the README, diagram, and runbook delivered at the end.

---

## 12. New buttons and doors (API additions)

```
POST /builds/{id}/proposals           submit a proposal (design + plan + rate)
GET  /builds/{id}/proposals           list proposals + scorecards
POST /builds/{id}/proposals/{pid}/award   pick a proposal (or run a bake-off)
POST /builds/{id}/milestones/{m}/submit   deliver a milestone
GET  /builds/{id}/milestones/{m}/quality  automated quality report (sanitized)
POST /builds/{id}/milestones/{m}/review   reviewer decision
POST /builds/{id}/worklog             add evidenced time
POST /builds/{id}/handover            deliver docs, diagram, runbook
```

---

## 13. Where this fits in the roadmap

The `build` track comes **after** the `optimize` track works. It needs everything
the `optimize` track has (contracts, consent, escrow, sandbox) **plus** proposals,
review rubrics, milestones, and the quality gate. Treat it as its own phase:

- **P6 — Proposals & review:** briefs, proposals, rubric scoring, bake-off.
- **P7 — Milestones & quality gate:** milestone escrow, automated quality scans,
  human review, handover.

---

## 14. What can go wrong here, and how we reduce it

| What can go wrong | How we reduce it |
|---|---|
| A beautiful design that cannot actually be built | Milestone 1 is a runnable skeleton; payment only on delivery. |
| Hourly hours padded | Milestone-based release; evidenced work logs; caps; review. |
| A vague brief leads to endless "that's not what I meant" | Requirements must be checkable; acceptance checklist agreed before work starts. |
| Low-quality or insecure code shipped anyway | Automated quality gate (tests, SAST, dependency/license scans) plus human review. |
| An inexperienced worker wins a high-stakes build | Trust tiers; proposal rights earned through track record. |
| The company overpays early and gets stuck | Escrow per milestone; a not-to-exceed cap; a dispute window. |
| A dependency with the wrong license sneaks in | SBOM and an allowed-license list, enforced automatically. |

---

## 15. Open decisions

- Whether proposals are paid (a design bounty) or free to submit as a competition.
- The default pay shape (hourly, fixed-price, or a design/build mix).
- The exact rubric weights, and who reviews — the company alone, or with platform
  senior reviewers.
- How strict the trust tiers are before a worker may submit a design proposal.
- Which stacks the platform supports first, beyond the initial Java, Rust and
  Python runtimes.
