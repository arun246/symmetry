# Architecture decision records

An **ADR** is a short note that records a decision that is expensive to change
later. It says what we chose, why, what we rejected, and what the choice costs.

These ADRs describe the runner, payment model and exercise authoring track added
after the 0.0.1 UI release. They are the source of truth for the appliance, the
neutral evaluator, the AI adjudicator, updates, language runtimes, licensing and
the public exercise flow.

| ADR | Decision |
|---|---|
| [0001](0001-runner-isolation.md) | KVM microVM is the outer isolation boundary; plain containers are not enough |
| [0002](0002-licensing.md) | ~~Apache-2.0 agent/SDK, AGPL-3.0 server~~ (superseded by 0008) |
| [0003](0003-ai-adjudication.md) | AI-final payment with escrow, evidence and one human appeal |
| [0004](0004-updates-and-failover.md) | Signed OTA plus offline bundles; lease-and-retry failover |
| [0005](0005-language-runtimes.md) | Java 21, Rust/WASM and Python 3.13 runtimes |
| [0006](0006-cloud-appliance.md) | qcow2/OVA, AWS AMI and GCP image; nested-virt support tiers |
| [0007](0007-exercise-authoring-track.md) | Separate exercise context; public GitHub, authoring + static checks only, no execution or payment yet |
| [0008](0008-single-agpl-licence.md) | One licence for the whole repository: AGPL-3.0 |

## Status values

- **Proposed** — written, not agreed.
- **Accepted** — agreed; code and docs should follow it.
- **Superseded** — replaced by a newer ADR; keep it for history.

## Changing an ADR

Do not rewrite an accepted decision to hide history. Add a new ADR that
supersedes it, and update the index above.
