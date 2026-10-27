# ADR-0002: Apache agent, AGPL server, commercial support

- **Status:** Superseded by [ADR-0008](0008-single-agpl-licence.md)
- **Date:** 2026-10-22
- **Related:** [ADR-0001](0001-runner-isolation.md), [ADR-0006](0006-cloud-appliance.md)

## Context

The owner wants a **Red Hat-style** model: open-source software, published
updates, community access, and a paid supported product. The publisher owns the
hardware and electricity. Symmetry owns the software, updates and support.

"RHEL-style" is a business model, not a code licence. RHEL itself is a commercial
distribution with subscription terms, trademark control and restrictions on
redistributing subscribed binaries. We should not copy those restrictions into
our own code.

## Decision

Split the licence by what the code does:

| Path | Licence | Why |
|---|---|---|
| `runner/` agent, appliance glue, protocol schemas | **Apache-2.0** | Publishers and cloud teams can inspect, package and integrate it without copyleft friction. |
| `wasm-sdk/` | **Apache-2.0** | The module SDK should be easy to adopt. |
| `platform/` control plane | **AGPL-3.0** | A competitor cannot host a modified closed version as a service without publishing its changes. |
| `ai/` service | **AGPL-3.0** | It is part of the server-side decision path and must stay inspectable. |
| `ui/` console | **AGPL-3.0** | It ships with the server and is not a separate reusable library. |
| `deploy/` local and reference deployment files | **AGPL-3.0** | Keeps the reference deployment aligned with the server. |
| Docs and diagrams | **Not decided** | Do not add a blanket licence until the owner chooses one. |

Commercial layer, kept private:

- release signing keys
- holdout tests, oracles and hidden data
- build and release infrastructure
- hosted control plane operations
- trademark and brand
- certifications and audit reports
- paid support, update subscriptions and SLAs

The **appliance base OS is AlmaLinux 9**, not a redistributed RHEL qcow2. RHEL
images have subscription and redistribution constraints. AlmaLinux gives the
RHEL-compatible ABI without that problem.

Normal contributions use the **Developer Certificate of Origin (DCO)**. A
corporate CLA or a commercial exception to AGPL is a separate written agreement.

## Consequences

- The agent and SDK are easy for publishers, cloud teams and package
  distributions to adopt.
- The control plane is open source, but a hosted competitor must share changes.
- Some enterprises avoid AGPL by policy. A commercial exception or a hosted-only
  offer may be needed for them; that is a sales decision, not a code change.
- The project is **not** "RHEL licensed". We copy the open-core business model,
  not RHEL's EULA, trademarks or binary redistribution rules.
- Every dependency still needs an SBOM and an allowed-licence check.
- Legal review is required before publishing binaries or signing keys.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Apache-2.0 everywhere | Easiest adoption, but a competitor can host a closed modified control plane. |
| Proprietary core, Apache agent only | Strong IP control, weaker community trust and harder enterprise security review. |
| Source-available, subscription-only | Closest to RHEL's restrictions, but not open source and likely community backlash. |
| GPL-3.0 everywhere | Strong copyleft, but less suitable for the agent/SDK and still not the network-use protection of AGPL. |
| RHEL qcow2 appliance | Per-host subscriptions and restricted redistribution; wrong fit for publisher-owned hardware. |

## References

- [Licensing map](../../LICENSING.md)
- [Current licence](../../LICENSE) (AGPL-3.0; supersedes this ADR)
- [Contributing](../../CONTRIBUTING.md)
