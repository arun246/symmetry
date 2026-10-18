//! Example module: a deterministic, pure byte transform.
//!
//! This first version performs a byte-for-byte copy so the pipeline
//! (compile → load in Chicory → run → meter) can be exercised end to end. Replace
//! the body of [`run`] with real resize logic once the runner is in place.
//!
//! Rules for every module:
//! * no host imports — no file/network/time access,
//! * deterministic — same input always yields the same output,
//! * bounded — the host meters fuel and memory.

use symmetry_sdk::{export_bytes, read_input};

/// Entry point called by the host. Returns a length-prefixed output buffer.
///
/// # Safety
/// `input_ptr`/`input_len` must describe a buffer allocated by the host via
/// `symmetry_alloc`.
#[no_mangle]
pub unsafe extern "C" fn run(input_ptr: *const u8, input_len: usize) -> *mut u8 {
    let input = read_input(input_ptr, input_len);
    export_bytes(input)
}
