# ADR-0007: Exercise authoring track before execution

- **Status:** Accepted
- **Date:** 2026-10-23
- **Related:** [ADR-0001](0001-runner-isolation.md), [ADR-0005](0005-language-runtimes.md)

## Context

The contract track describes `optimize` work: improve an existing module and judge
it against hidden tests. The first usable product also needs a simpler path where a
publisher can turn a small public function into a task, and a worker can accept it
and submit an attempt. This is the **exercise** (or stub) track.

The full runner is not available yet. There is no `/dev/kvm` on the development
host, no neutral evaluator, no escrow provider and no GitHub App. Claiming that
this track executes or pays for code would be false.

## Decision

1. **Same application, separate bounded context.** Exercises live in
   `io.symmetry.exercise` with their own `exercise*` tables. The `contract` module
   remains the `optimize` track; the two do not share acceptance or submission
   rows.
2. **Java first.** A `StubExtractor` interface keeps the authoring flow open to
   Python and Rust later. Only the Java parser is implemented in this slice.
3. **Public GitHub only, pinned by commit.** Until a GitHub App exists, the import
   accepts public repository URLs, reads the default branch, resolves the current
   commit SHA, and stores that SHA with the source. No GitHub token is stored. A
   missing licence is shown as a warning, and the source is scanned for obvious
   secrets.
4. **Authoring and static checks only.** Publishers create a draft, import source,
   remove a method body, add public and hidden tests, and publish a task document.
   Workers accept, open a workspace and submit text. The server performs syntax
   and forbidden-API checks. It does **not** compile, run tests, deploy or execute
   untrusted code. `WorkspaceView.executionEnabled` is always `false`.
5. **Price is display-only.** The task document states the offered amount. There
   is no escrow, payment, AI adjudication or appeal in this track yet.
6. **Acceptance is still signed.** A worker signs the exact canonical message
   `accept:exercise:<exerciseId>:<version>:<sha256(taskDocument)>` with Ed25519.
   The server verifies the signature, requires the configured agreements, and
   uses the authenticated actor as `workerId` (never a request field).
7. **The public task list is the root page.** `GET /exercises` is public and `/`
   renders it. Drafts and hidden tests are not public.
8. **Publisher ownership is role-aware.** Only an admin, or a publisher in the
   exercise's organization, may see hidden tests and source content. A worker in
   the same organization is still a worker, not an owner.

## Consequences

- The browse → author → accept → submit flow can be used and tested now, without
  pretending the sandbox exists.
- Submissions are records, not verdicts. They carry `SUBMITTED` or
  `STATIC_FAILED`, not a correctness score.
- Private repositories and GitHub App installation are blocked until a GitHub App
  is registered.
- The exercise tables will need a migration when a real evaluator binds a signed
  result or a payment decision to a submission.
- Hidden tests and source content are protected by role checks in the application;
  they are not protected by a separate data store or TEE.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Put exercises in the existing `contract` tables | Mixes two lifecycles and makes `optimize` queries harder; a separate context is cheaper to evolve. |
| Support private GitHub with a personal access token | Long-lived secrets in the server violate the trust model; wait for a GitHub App. |
| Run `javac`/tests in this slice | There is no microVM boundary; a plain process is not acceptable under ADR-0001. |
| Convert Java to WASM for quick execution | Throws away the JIT and measures the wrong target (ADR-0005). |
| Let any member of the publisher's organization see hidden tests | Workers can share an organization; ownership must be role-aware. |
| Move money now | Escrow, AI adjudication and appeals are not implemented; a displayed price is honest. |
| Make the root page a dashboard | The user asked for the public task list as the index; discovery comes before sign-in. |

## References

- [Design: exercise authoring track](../DESIGN.md#the-exercise-authoring-track)
- [Stack: Java API libraries](../STACK.md#41-java-api--platform)
- [Implementation: exercise track](../IMPLEMENTATION.md#3a-exercise-track-implemented)
- `platform/src/main/resources/db/migration/V4__exercise_track.sql`
