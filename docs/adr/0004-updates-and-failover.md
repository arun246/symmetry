# ADR-0004: Signed updates and lease-based failover

- **Status:** Accepted
- **Date:** 2026-10-22
- **Related:** [ADR-0001](0001-runner-isolation.md), [ADR-0002](0002-licensing.md), [ADR-0006](0006-cloud-appliance.md)

## Context

Publishers own the hardware and electricity. Symmetry cannot rely on inbound SSH
or a human visiting the site. The appliance must update itself and survive a host
failure without losing work or silently running tampered code.

The software will be open source. That makes it inspectable, but it does **not**
make a downloaded binary trustworthy. An attacker who controls the mirror or the
network can still ship a modified image.

## Decision

### Updates

- The appliance pulls **signed OTA metadata and bundles** over outbound mTLS.
- Air-gapped sites import the same **signed offline bundle**.
- Use TUF-style metadata: version, hashes, expiry, key rotation and
  anti-rollback.
- Release-signing keys live in an HSM or KMS, never on a build runner.
- Every release includes an SBOM and build provenance.
- A/B partitions (or an A/B application image) with health checks and automatic
  rollback.
- Supported versions: current and previous minor. Security patches follow a
  published SLA.
- No inbound SSH. Break-glass access requires customer approval, a time limit and
  an audit record.

### Failover

- Jobs are **leased** with a deadline and a nonce.
- Results are **idempotent** and **signed** by the runner key.
- If a host dies, the lease expires and the job is retried on another runner.
- If a runner is compromised, its key is revoked, its results are quarantined and
  payment-critical work is re-run on the neutral evaluator.
- No live migration in V1. A running microVM is not moved; the job is re-run.
- The control plane is the source of truth for job state; the appliance keeps only
  a local cache/queue.

## Consequences

- Publishers can patch without opening a port.
- A stolen signing key is catastrophic; key custody and rotation are part of the
  product.
- Offline bundles add a manual release process and a verification step.
- Re-running a job costs compute, but avoids unsafe migration and is simple to
  audit.
- The appliance must be mostly stateless; local state is limited to cache,
  updates and diagnostics.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Unsigned updates | Open source does not authenticate the binary; one mirror compromise owns every publisher. |
| Online-only updates | Many enterprises and critical sites need offline import. |
| Inbound SSH for support | Too much access, hard to audit, and a large attack surface. |
| Live migration | Complex, device-specific and unnecessary for V1. |
| Run jobs only once | A host failure would lose work and confuse payment. |

## References

- [Design: fail closed](../DESIGN.md#3-rules-the-design-must-never-break)
- [Licensing and supply chain](0002-licensing.md)
