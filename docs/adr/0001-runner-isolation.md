# ADR-0001: KVM microVM is the runner isolation boundary

- **Status:** Accepted
- **Date:** 2026-10-22
- **Supersedes:** the assumption that a plain container is enough for the Java path
- **Related:** [ADR-0005](0005-language-runtimes.md), [ADR-0006](0006-cloud-appliance.md)

## Context

The publisher owns the hardware and electricity. Symmetry owns the software,
updates and support. Worker code is untrusted.

The existing design already has two inner sandbox tiers:

- the Java path runs in a JVM-tier sandbox;
- the Rust path runs as `wasm32` in Chicory.

Those tiers protect the *language runtime*. They do not protect the **host** if
the container, the JVM or the host kernel is compromised. A chroot is not a
security boundary. A normal Docker container shares the host kernel.

The platform must run untrusted Java, Rust and Python for payment-critical work.
It must fail closed and must produce evidence that the job ran where it claimed.

## Decision

Every publisher-hosted evaluation runs inside a **KVM microVM**.

```
AlmaLinux 9 host
  └── Symmetry appliance
        ├── outbound mTLS agent
        ├── preflight and health
        ├── signed OTA updater
        └── KVM
              └── Firecracker or Kata microVM
                    ├── Java 21 (JVM tier)
                    ├── Rust (wasm32 in Chicory)
                    └── Python 3.13 (native process)
```

Rules:

1. **The microVM is the outer boundary.** The JVM tier, Chicory and Python
   process isolation stay as inner layers. A container alone is not a trust
   boundary.
2. **Required host features:** x86-64, hardware virtualisation (`VT-x` or
   `AMD-V`), `/dev/kvm`, and KVM loaded. The baseline is `x86-64-v2`.
3. **No network.** The guest has no network device. The agent communicates over
   `vsock` or an equivalent local channel.
4. **No secrets in the guest.** The job envelope contains only what the task
   needs. Host credentials, publisher data and hidden tests stay outside.
5. **Read-only root, writable tmpfs.** Only an explicit output directory is
   writable, and it is size-capped.
6. **Limits are enforced outside the guest.** CPU, memory, disk, wall clock and
   output size are enforced by the host, not trusted from the guest.
7. **Results are signed.** The runner signs the result with its Ed25519 key; the
   control plane verifies it before accepting the verdict.
8. **Fail closed.** If `/dev/kvm` is missing, preflight fails. There is no silent
   fallback to Docker, chroot or `runc`.

Firecracker is the preferred microVM monitor because it boots quickly and has a
small device model. Kata Containers is used on the neutral Kubernetes fleet where
we own the nodes and want Kubernetes scheduling.

## Consequences

- Hosts must support KVM. Some cloud VMs do not expose nested virtualisation, so
  they are unsupported until certified (see [ADR-0006](0006-cloud-appliance.md)).
- The appliance is larger and more stateful than a container image.
- We must build and maintain guest images, a preflight checker and a host agent.
- We get a real boundary between untrusted code and the publisher's other work.
- Live migration is not part of V1; a failed host means the job is retried, not
  moved while running (see [ADR-0004](0004-updates-and-failover.md)).
- The existing Java-container wording in `AGENTS.md` and
  `docs/IMPLEMENTATION.md` is now an **inner** tier, not the whole sandbox.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Docker/runc only | Shares the host kernel; too weak for hostile code and payment-critical evaluation. |
| chroot | Not a security boundary; only changes the visible filesystem. |
| Bare metal with no isolation | One malicious submission owns the publisher's machine. |
| Cloud-first | Publisher owns hardware and electricity; cloud also has nested-virt gaps. |
| gVisor only | Useful, but a different boundary and weaker for some workloads; not the primary choice. |
| Java-to-WASM (TeaVM) | Drops the JIT and measures the wrong target. |

## References

- [Design: eval fabric](../DESIGN.md#4-how-the-whole-thing-is-wired-together)
- [Implementation: sandboxes](../IMPLEMENTATION.md#5-m3--the-builders-and-the-sandboxes-the-heart-of-p2)
- [AGENTS.md](../../AGENTS.md)
