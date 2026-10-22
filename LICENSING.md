# Licensing map

Symmetry uses a **split licence**. The goal is a Red Hat-style model: open-source
software, published updates, community access, and a paid supported product.

This file is a map, not legal advice. Review it before publishing binaries or
signing keys.

## Code

| Path | Licence | Why |
|---|---|---|
| `runner/` | [Apache-2.0](LICENSE-APACHE) | The publisher appliance and agent should be easy to inspect, package and integrate. |
| `wasm-sdk/` | [Apache-2.0](LICENSE-APACHE) | The module SDK should have the lowest possible adoption friction. |
| `platform/` | [AGPL-3.0](LICENSE-AGPL) | A hosted competitor must share modifications to the control plane. |
| `ai/` | [AGPL-3.0](LICENSE-AGPL) | The adjudication service is part of the server-side decision path. |
| `ui/` | [AGPL-3.0](LICENSE-AGPL) | The console ships with the server and is not a separate reusable library. |
| `deploy/` | [AGPL-3.0](LICENSE-AGPL) | The reference deployment stays aligned with the server. |
| `docs/` | Not decided | Do not add a blanket licence until the owner chooses one. |

Third-party dependencies keep their own licences. The SBOM and dependency scan
are the authority for those; this file does not relicense them.

## What stays private

- release signing keys and key custody
- holdout tests, oracles and hidden data
- build and release infrastructure
- hosted control-plane operations
- trademarks and brand
- certifications and audit reports
- paid support, update subscriptions and SLAs

## Commercial use

The AGPL does not stop a company from using Symmetry internally. It does require
that a modified version offered to users over a network provides its source under
the AGPL.

A **commercial exception** for embedding or hosting a closed version is available
only under a separate written agreement. Contact the owner before promising one.

## Appliance base OS

The publisher appliance uses **AlmaLinux 9**, not a redistributed RHEL qcow2.
RHEL images carry subscription and redistribution constraints. AlmaLinux gives a
RHEL-compatible ABI without those constraints.

## Contributions

See [CONTRIBUTING.md](CONTRIBUTING.md). Contributions to a path are accepted under
that path's licence, with a Developer Certificate of Origin sign-off.
