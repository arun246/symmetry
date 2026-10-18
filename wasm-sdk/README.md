# Symmetry WASM SDK

Rust modules compiled to `wasm32-unknown-unknown` and executed in **Chicory**
(a pure-JVM WebAssembly runtime) with **no host imports** and fuel/memory metering.

## Layout

- `sdk/` — `symmetry-sdk`, the shared ABI helpers (`symmetry_alloc`,
  `symmetry_dealloc`, `export_bytes`, `read_input`).
- `modules/image-resize/` — an example module (`crate-type = ["cdylib"]`) exporting
  `run`.

## ABI in one paragraph

The host reserves a buffer with `symmetry_alloc`, writes the input bytes, calls
`run(input_ptr, input_len)`, then reads the **length-prefixed** result
(`[u64 little-endian length][payload]`) and frees both buffers with
`symmetry_dealloc`.

## Build (needs Rust, not installed on this host)

```sh
rustup target add wasm32-unknown-unknown
cargo build --release --target wasm32-unknown-unknown -p image-resize
# -> target/wasm32-unknown-unknown/release/image_resize.wasm
```

> Reminder: this project's **Java** path uses a JVM-tier sandbox, not WASM.
> Rust is the language whose evaluation target is genuinely WASM.
