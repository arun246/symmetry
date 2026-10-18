# Symmetry: Future of software tech work — Planning for the Future (so we do not have to rewrite)

This document explains **why we must plan ahead**, and **what to build now** so
that future needs can be added as new pieces instead of forcing us to tear the
whole thing down and start over.

We wrote it in plain language. If a special word appears, it is explained right there.

---

## 1. The mistake we want to avoid

Most of the danger is not a missing feature. The danger is building a **rigid
center**: the job card, the code-runner, the answer key, and the scoring rules all
hardcoded for **one single kind of work**.

A helpful picture: think of a **power strip with one weird socket**. If your only
socket fits one special plug, then every new appliance needs the whole strip
rebuilt. If instead you install **standard sockets** now, you can plug in anything
later without rebuilding.

So: in the first version we ship **one kind of work**, but the **connectors**
(sockets) must fit many kinds.

(Engineers call connectors like this "seams" or "extension points." We will just
call them **sockets**.)

---

## 2. "Backend vs frontend" is the wrong way to split things

People usually ask "is this only for backend, or also frontend?" That split is too
simple. What really matters are **four separate questions**. Your first version
answers them one way; future work may answer them differently.

| Question | First version (V1) | Other answers to expect |
|---|---|---|
| **How does the code behave?** | A pure, predictable function: same inputs → same output | Stateful (remembers things) → talks to the network → runs many things at once → spread across many machines → has a random/learning part (machine learning) |
| **How do we know it is correct?** | Property tests, fuzzing, comparison with old code, secret exam | A simulator → visual comparison → statistics → a mathematical proof |
| **What do we measure?** | Speed and memory and file size | How many jobs per second → how big the web page is and how fast it feels → how good a machine-learning answer is → energy use and cost |
| **Where does the code run?** | In a small safe box with no outside connections | Inside a web browser → across many containers → inside a whole operating system or a virtual machine → on a graphics/gaming chip (GPU) |

**The key sentence:** frontend, backend services, operating-system work, and
machine learning are **different answers to these questions**. They are not bigger
versions of the first kind of work — they are *other cells* in the table above.

The real risk is not "we forgot frontend." The real risk is **baking one cell into
the center of everything**.

---

## 3. The assumptions that would force a rewrite

These are the specific things that, if hardcoded, become painful later:

1. **"Code always becomes a safe runnable file, judged by predictable tests."**
   This breaks for web pages (needs a browser), services that remember state
   (need a fake database and a fake clock), operating-system work (needs a
   simulator), and machine learning (never perfectly repeatable).
2. **"The safe box has no outside connections at all."** This is elegant for pure
   functions, but fatal for anything that needs *controlled* use of the outside
   world. Later we need a "keycard" model: each module is handed a **list of
   doors it may open** (a fake database, a fake clock, one approved network
   address). In V1 the keycard is blank — no doors.
3. **"The answer key is always tests the company wrote."** Breaks when correctness
   is judged by a simulator, a proof checker, a visual comparison, or a statistical
   threshold.
4. **"We always measure the same fixed numbers."** Every new domain would need us
   to change the stored shape of the data. Instead, keep the measurements as a
   **flexible list of named numbers**.
5. **"A job card is frozen, and a worker submits exactly once."** The original idea
   lets workers **ask for more data or AI help** — that is a back-and-forth
   conversation, not a single fixed record. It needs a **lifecycle** (a sequence of
   states a job passes through).
6. **"Only a fixed short list of languages is allowed."** The design now says *any*
   language the task names. Adding C, Go, or TypeScript later must be plugging in a
   new compiler, not editing the center.

---

## 4. Where each future kind of work would land

### 4.1 Web pages and user interfaces (frontend)

- **Where it runs:** a headless browser (a browser with no visible window) and the
  web page's internal structure (the "DOM"), not our WASM box.
- **Predictability:** weak. Fonts, the graphics chip, timing, and the order in
  which parts load can change the result. Our "run twice and compare exactly" rule
  cannot apply as-is.
- **How we check correctness:** picture comparison (fuzzy, with a tolerance),
  interaction tests (for example, Playwright), accessibility checks (rough rules),
  and layout rules. None give a clean yes/no.
- **What we measure:** how big the page's code files are, how fast the page feels
  (LCP/INP/CLS are standard "how fast does it feel" numbers), and memory inside
  the browser — not memory of a normal program.
- **Secret-keeping:** web pages leak both the product's intention and its branding.
  The "what may be revealed" assessment changes a lot.

**Conclusion:** frontend needs a **different runner, a different answer key, and
different scoring**. It is *not* "HTML instead of Rust." We **defer** it, but we do
**not** lock it out.

### 4.2 Backend services that remember state

- They need a blank keycard upgraded to a **seeded keycard**: a prepared test
  database, a fake clock, and one approved network address.
- We measure how many jobs per second and the slowest tail under heavy load, and we
  watch for memory that keeps growing over long runs (a "leak").

### 4.3 Operating-system-level work

- This is not one module with one answer key; it is a **whole system** whose
  behavior emerges from many parts.
- It needs a **simulator** answer key (QEMU, a network simulator, deliberate chaos
  testing). This is a separate product line, not just a new runner.

### 4.4 Machine learning / data modules

- Never perfectly repeatable. The secret exam becomes separate training and testing
  data, and there is a serious risk of **memorizing the exam** (overfitting).
- "How good is the answer" (accuracy) must be weighed against resources, rather
  than strict Pareto.

### 4.5 Modules we can prove correct mathematically

- The answer key is a **proof checker** (SMT, Coq, F*, refinement types) — its own
  separate checkpoint type.

---

## 5. The seven sockets to install now

We install these connectors now, but we only plug in the first version's shape.
Each is a place future kinds of work can attach without touching the center.

1. **A "work kind" label on the job card** (`compute | service | ui | data |
   system`). V1 only uses `compute`.
2. **A runner connector:** a standard way for a runner to `compile`,
   `give-it-a-keycard`, `run`, and `collect numbers`.
3. **An answer-key connector:** the gate becomes a **list** of checkers. V1 plugs
   in property / fuzz / comparison / secret exam.
4. **Measurements as a flexible list** of named numbers — never fixed fields.
5. **A "keycard" object** instead of the special "no outside connections at all"
   case. In V1 the keycard is empty.
6. **A list of compilers** (a "toolchain registry") instead of `if Java ... else if
   Rust`.
7. **A job lifecycle** (`draft → negotiated → accepted → submitted → judged →
   merged`) even if V1 only walks the shortest path.

**With these sockets, adding a web-page runner later is just plugging in a new
appliance. Without them, it is a full rebuild.**

**Status (this build).** Sockets 1 (work kind), 7 (lifecycle), 6 (toolchain
registry), 5 (keycard), 3 (the gate as a list of checkers), and 4 (flexible named
metrics) now exist as types in `platform/`. Socket 2 (the runner connector) is
declared but filled in only in M3. The first cell — compute — is the only one wired
up; every other value is a label waiting for its runner.

---

## 6. Install now, or wait?

| Concern | Install the socket now | Fill it in later |
|---|---|---|
| Work kind | the `WorkKind` label on the card | runners for non-compute work |
| Safe-box keycard | the `Keycard` object (empty in V1) | seeded database, fake clock, approved addresses |
| Answer key | the gate as a **list** of checkers | simulator, visual, statistical, proof checkers |
| Measurements | a flexible named-number list | jobs-per-second, page-feel, answer quality |
| Compilers | a **registry** connector | C, Go, TypeScript, mixed-language |
| Lifecycle | the sequence of states | asking-for-data / AI-help conversations, revisions |
| Scoring | a rule behind a connector | tolerance/quality models beyond strict Pareto |

---

## 7. Other future needs worth designing toward

- **Back-and-forth work:** asking for more data, AI help, clarifying the task, and
  revising over several rounds — a *conversation*, not a single submission.
- **Several workers on one module:** splitting the job, coordinating, and giving
  credit.
- **Long-term upkeep money:** royalties, ongoing bug rewards on merged modules, and
  payments over time.
- **Different secrecy levels:** a locked safe room (TEE), an on-premise machine, or
  fully offline; and keeping different companies' data apart.
- **Modules built from more than one language:** where two languages meet
  (for example, Java calling Rust).

---

## 8. The bottom line

**The first version ships exactly one cell of the table, but its connectors must
fit every cell.** The danger is not frontend — it is a rigid center.

Install the seven sockets, ship the first cell (compute + tests + speed/memory +
safe box), and then make the **first proof that the sockets work** by plugging in a
second runner (a web-page or service one) *early* — before the product has hardened
around a single kind of work.
