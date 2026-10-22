# ADR-0003: AI-final payment with escrow and one human appeal

- **Status:** Accepted
- **Date:** 2026-10-22
- **Related:** [ADR-0001](0001-runner-isolation.md), [ADR-0004](0004-updates-and-failover.md)

## Context

The AI that helped write a task plan is also used as the third-party adjudicator
for the final payment decision. The owner wants **AI-final** decisions with the
minimum human involvement.

Payment is different from a code review. A wrong decision moves money, affects a
worker's reputation and can be challenged. A probabilistic model cannot be the
only record of why a payment happened.

The platform already has deterministic gates (build, property tests, fuzzing,
differential checks, holdout). Those produce evidence; the AI decides what the
evidence means when the case is ambiguous.

## Decision

Use a **two-key** payment path:

1. **Deterministic gate first.** Build, correctness, security and resource
   checks run before the AI is called. A submission that fails the gate never
   reaches payment adjudication.
2. **AI adjudicator.** It receives a structured evidence bundle:
   - contract and task-document hashes
   - gate results and sanitized logs
   - metric vector
   - policy version
   - submission hash and language
   It does **not** receive the worker's identity, the publisher's private
   chain-of-thought, signing keys or raw hidden data.
3. **AI decision is final on the first instance.** It returns `PAY`, `DENY` or
   `NEEDS_REVIEW`, with reasons and evidence references.
4. **Escrow.** The money is held before work starts. It is released only after the
   AI decision and the appeal window.
5. **One human appeal.** A worker may appeal once with evidence. A human reviewer
   sees the same evidence bundle plus the AI's reasons. The reviewer can uphold,
   reverse or remand.
6. **Audit.** Store model name, model version or hash, prompt hash, input hash,
   output hash, policy version, timestamps and reviewer identity.
7. **Liability cap and published policy.** The contract states the cap, the
   appeal window and the fact that an AI makes the first decision.
8. **Model isolation.** The same model may be used initially, but it runs in a
   dedicated adjudicator role and context. The interface allows a different model
   later without changing the payment flow.
9. **No training on customer code.** Publisher code and worker submissions are
   not used to train the adjudicator without explicit permission.

`NEEDS_REVIEW` and a successful appeal are the only paths to a human. Everything
else is AI-final.

## Consequences

- Payment is fast for the common case.
- The system can explain and replay a decision later.
- Workers get a safety valve without a human reviewing every payment.
- The AI provider is a regulated-adjacent dependency: availability, data
  residency, retention and cost must be in the contract.
- Automated decision-making may trigger legal rights to explanation or human
  review in some jurisdictions. The appeal path is the answer; legal review is
  still required.
- A wrong AI decision can still cost money; the liability cap and escrow are not
  optional.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| AI-final with no appeal | Cheapest, but no safety valve and weak worker trust. |
| Human reviews every payment | Safest, but contradicts the owner's decision and does not scale. |
| AI advisory, human final | Same cost problem; slower than the chosen model. |
| AI appeal only above a value threshold | Creates two tiers of fairness; the evidence path is the same either way. |
| Deterministic tests only | Cannot resolve ambiguous quality or specification cases. |

## References

- [Design: payment and trust](../DESIGN.md#7-how-workers-get-paid-and-trusted-marketplace)
- [Design: safety and legal](../DESIGN.md#8-safety-secret-keeping-and-legal)
