# ADR-0006: qcow2, AWS and GCP appliance images

- **Status:** Accepted
- **Date:** 2026-10-22
- **Related:** [ADR-0001](0001-runner-isolation.md), [ADR-0004](0004-updates-and-failover.md)

## Context

Publisher teams need an installation that is no harder than importing an image
and pasting a join token. Some publishers run bare metal or Proxmox; some run
cloud VMs. The appliance needs KVM either way.

Cloud VMs do not all expose `/dev/kvm`. Nested virtualisation is provider- and
instance-specific. A cloud image that boots but cannot start a microVM is a trap.

## Decision

### Image formats

| Target | Format | Tier |
|---|---|---|
| Bare metal, Proxmox, plain KVM | qcow2 and OVA | **Tier 1** |
| AWS | AMI built with Packer | **Tier 2** |
| GCP | GCP image built with Packer | **Tier 2** |
| Azure and other clouds | Not V1 | Best effort later |

### Host and guest

- Host OS: **AlmaLinux 9**.
- Guest OS: **Alpine** for minimal microVMs; **AlmaLinux/glibc** where Java or
  Python needs it.
- Do **not** ship a RHEL qcow2. RHEL images have subscription and redistribution
  constraints.

### First-boot experience

- cloud-init sets the hostname, network, join token and tags.
- preflight checks `/dev/kvm`, CPU flags, KVM modules, memory, disk and nested
  virtualisation.
- clear failure message if the host cannot run microVMs; no silent container
  fallback.
- outbound mTLS only; no inbound SSH.
- one join token; no shared long-lived password.

### Cloud scope

- Support only instance types that expose `/dev/kvm` and pass preflight.
- Publish the certified instance list; everything else is “best effort”.
- Terraform modules per cloud, not one generic module with hidden differences.
- Do not claim “all clouds” in V1.

## Consequences

- Three image formats mean three build, test and support pipelines.
- Cloud teams get a familiar image; bare-metal teams get the most reliable path.
- Preflight becomes a product feature, not a script.
- The neutral evaluator remains on Kubernetes + Kata/Firecracker on hardware we
  control; the publisher appliance is not a Kubernetes cluster.
- Cloud provider changes can break nested virtualisation; the certified list must
  be tested, not assumed.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| RHEL qcow2 | Per-host subscriptions and restricted redistribution. |
| Arch/Omarchy | Rolling release and desktop-first; wrong lifecycle for a signed appliance. |
| Alpine host | musl compatibility, short lifecycle and thinner KVM server tooling. |
| Docker-only deployment | Wrong isolation boundary. |
| All clouds in V1 | Too much certification and support surface. |
| Cloud-first | Publisher owns hardware and electricity. |

## References

- [Licensing](0002-licensing.md)
- [Runner isolation](0001-runner-isolation.md)
- [Stack: deployment](../STACK.md)
