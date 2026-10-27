# Licence

Symmetry is **one licence, not a split**: the whole repository — `platform/`,
`ai/`, `ui/`, `wasm-sdk/`, `runner/`, `deploy/` and `docs/` — is under the
**GNU Affero General Public License v3.0** (AGPL-3.0).

The full text is in [LICENSE](LICENSE).

This file is a map, not legal advice. Review it before publishing binaries or
signing keys.

## Why AGPL-3.0

The goal is a Red Hat-style model: open-source software, published updates,
community access, and a paid supported product.

AGPL-3.0 keeps the software fully open while closing the network loophole: if
someone offers a modified Symmetry to users over a network, they must offer the
corresponding source. That is the protection the hosted control plane and the
adjudication service depend on. One licence for every path also removes the
"which licence is this file under?" question for publishers, contributors and
package builders.

Code in this repository is **not** MIT, Apache-2.0, dual-licensed or
source-available. Earlier revisions used a split (Apache-2.0 for the agent and
SDK); [ADR-0008](docs/adr/0008-single-agpl-licence.md) supersedes
[ADR-0002](docs/adr/0002-licensing.md).

Third-party dependencies keep their own licences. The SBOM and dependency scan
are the authority for those; this file does not relicense them.

## What stays private

The licence covers code, not the commercial layer:

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

See [CONTRIBUTING.md](CONTRIBUTING.md). Contributions are accepted under
AGPL-3.0, with a Developer Certificate of Origin sign-off.
