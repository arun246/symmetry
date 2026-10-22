# ADR-0005: Java, Rust and Python runtimes

- **Status:** Accepted
- **Date:** 2026-10-22
- **Related:** [ADR-0001](0001-runner-isolation.md), [ADR-0006](0006-cloud-appliance.md)

## Context

V1 must evaluate Java, Rust and Python. The platform is polyglot by design: the
task committee chooses the language for `optimize`, and the worker proposes it for
`build`.

The runner must produce repeatable results without network access. It must not let
a submission fetch dependencies at runtime. It must also measure the right target:
the JVM for Java, WASM for Rust, and CPython for Python.

The host is AlmaLinux 9. Guest images may be Alpine (small) or glibc-based where a
language needs it. musl and glibc are not interchangeable.

## Decision

| Language | Runtime | Build | Notes |
|---|---|---|---|
| **Java 21** | JVM tier inside the microVM | fixed `javac` 21 with an offline Maven/Gradle cache | Keep the JIT; do not convert Java to WASM. |
| **Rust** | `wasm32` in Chicory inside the microVM | pinned `rustc`, `cargo vendor`, `wasm32-unknown-unknown` | V1 measures WASM. Native Rust can be a later toolchain tier. |
| **Python 3.13** | CPython inside the microVM | pinned interpreter and offline wheels | Native extensions are allowed only from the pinned wheel set. |

Rules:

1. **Pinned toolchains.** The exact compiler/interpreter version is part of the
   build record.
2. **Offline dependencies.** Vendor or mirror every allowed package. No `pip
   install` or `cargo fetch` from the network during a job.
3. **Separate build and run limits.** Compilation can use more CPU and time than
   the measured run.
4. **Per-language guest images.** A Java or Python image may use AlmaLinux/glibc;
   a minimal Alpine image is fine for WASM and small native jobs.
5. **Fingerprint the artifact.** Same source + same toolchain must give the same
   artifact; reject drift.
6. **No host imports for WASM.** Rust modules stay import-free in V1.
7. **Test the musl boundary.** Java JNI libraries and Python manylinux wheels may
   fail on Alpine. Use a glibc guest rather than patching around it.

## Consequences

- Three toolchains mean three sets of CVEs, size budgets and build images.
- Python packaging is the hardest to make reproducible; the allowed-wheel set is
  a product decision, not an implementation detail.
- Rust/WASM is safe but measures WASM, not native Rust. Native Rust needs a
  separate tier and a clear label.
- The larger images increase OTA size and boot time.
- The toolchain registry stays the extension point; adding a language must not
  edit the center.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Java and Rust only at launch | Contradicts the owner's decision to include Python. |
| Run everything in containers | Weak host boundary; see ADR-0001. |
| Convert all languages to WASM | Wrong performance target, especially for Java and Python. |
| Alpine everywhere | musl breaks Java JNI and many Python wheels. |
| Network installs during a job | Non-reproducible and an escape path. |

## References

- [Stack: toolchains](../STACK.md#44-rust-module-sdk--wasm-sdk)
- [Implementation: sandboxes](../IMPLEMENTATION.md#5-m3--the-builders-and-the-sandboxes-the-heart-of-p2)
