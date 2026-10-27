# ADR-0008: One licence — AGPL-3.0 for the whole repository

- **Status:** Accepted
- **Date:** 2026-10-27
- **Supersedes:** [ADR-0002](0002-licensing.md)
- **Related:** [ADR-0006](0006-cloud-appliance.md), [ADR-0007](0007-exercise-authoring-track.md)

## Context

ADR-0002 split the licence by path: Apache-2.0 for the runner agent and the
WASM SDK, AGPL-3.0 for the server, console and deployment. The split was meant to
lower adoption friction for the parts publishers install on their own hardware.

In practice it costs more than it returns:

- every new directory needs a licensing decision, and contributors guess wrong;
- package builders must inspect which path a file came from;
- the owner asked for **one** licence across the repository.

The server-side protection still matters: a competitor must not be able to host a
modified, closed version of the control plane or the adjudication service.
Plain GPL-3.0 would allow exactly that, because hosting is not distribution.

## Decision

License the entire repository under **AGPL-3.0**. Concretely:

- one file, [LICENSE](../../LICENSE), containing the GNU AGPL v3 text;
- `LICENSE-APACHE` is removed;
- `runner/`, `wasm-sdk/`, `platform/`, `ai/`, `ui/`, `deploy/` and `docs/` are all
  AGPL-3.0;
- the Cargo manifests declare `AGPL-3.0-only`;
- the commercial layer (signing keys, holdouts, build infrastructure, hosted
  operations, trademarks, certifications, support/SLA) stays private and is not
  relicensed by this decision;
- third-party dependencies keep their own licences; the SBOM remains the
  authority for them.

## Consequences

- One answer to "what licence is this?" for contributors, publishers and
  distributors.
- The network-use protection of AGPL-3.0 now also covers the runner agent and the
  SDK. Publishers can still inspect, build and run them; if they offer a modified
  version over a network, they must share the source.
- Some enterprises avoid AGPL by policy. That was already true for the server; it
  now applies to the agent and SDK too. A commercial exception or a hosted-only
  offer is a sales decision, not a code change.
- The Red Hat-style business model is unchanged: open code, paid support, signed
  updates and private holdouts.
- Legal review is still required before publishing binaries or signing keys.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Keep the Apache/AGPL split (ADR-0002) | Two answers to one question, extra review work, and the owner asked for one licence. |
| GPL-3.0 everywhere | Strong copyleft but no network-use protection; a competitor could host a closed modified platform. |
| Apache-2.0 everywhere | Maximum adoption, but gives away the control-plane protection entirely. |
| MIT for the SDK only | Reintroduces a split for a small adoption gain. |

## References

- [Licensing map](../../LICENSING.md)
- [AGPL-3.0](../../LICENSE)
- [Contributing](../../CONTRIBUTING.md)
- [Superseded ADR-0002](0002-licensing.md)
