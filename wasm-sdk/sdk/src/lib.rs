//! ABI helpers for Symmetry WASM modules.
//!
//! Modules are compiled to `wasm32-unknown-unknown` and run inside **Chicory**
//! with **no host imports**: a module may not touch the filesystem, the network,
//! the clock, or the process. It only transforms bytes.
//!
//! # The ABI
//!
//! The host:
//! 1. calls [`symmetry_alloc`] to reserve `input_len` bytes,
//! 2. writes the input bytes there,
//! 3. calls the module's `run(input_ptr, input_len)`,
//! 4. reads the returned **length-prefixed** buffer,
//! 5. calls [`symmetry_dealloc`] on both buffers.
//!
//! A length-prefixed buffer is `[u64 little-endian length][payload bytes...]`.
//! The host knows the input length; the module's output length is in the prefix.

use std::alloc::{alloc, dealloc, Layout};

const LENGTH_PREFIX: usize = 8;

/// Reserve `len` bytes of module memory for the host to write into.
#[no_mangle]
pub extern "C" fn symmetry_alloc(len: usize) -> *mut u8 {
    if len == 0 {
        return std::ptr::null_mut();
    }
    let layout = Layout::from_size_align(len, 1).expect("invalid layout");
    // Safety: layout has non-zero size (checked above).
    unsafe { alloc(layout) }
}

/// Free a buffer previously returned by [`symmetry_alloc`].
#[no_mangle]
pub extern "C" fn symmetry_dealloc(ptr: *mut u8, len: usize) {
    if ptr.is_null() || len == 0 {
        return;
    }
    let layout = Layout::from_size_align(len, 1).expect("invalid layout");
    // Safety: caller passes a pointer and length from symmetry_alloc/export_bytes.
    unsafe { dealloc(ptr, layout) }
}

/// Borrow the host-provided input bytes.
///
/// # Safety
/// `ptr`/`len` must describe a valid buffer allocated by [`symmetry_alloc`].
pub unsafe fn read_input<'a>(ptr: *const u8, len: usize) -> &'a [u8] {
    if ptr.is_null() || len == 0 {
        return &[];
    }
    std::slice::from_raw_parts(ptr, len)
}

/// Wrap output bytes as a length-prefixed buffer the host can read back.
///
/// The caller owns the returned pointer and must free it with
/// [`symmetry_dealloc`] using `8 + bytes.len()` as the length.
pub fn export_bytes(bytes: &[u8]) -> *mut u8 {
    let total = LENGTH_PREFIX + bytes.len();
    let out = symmetry_alloc(total);
    if out.is_null() {
        return out;
    }
    // Safety: `out` points to `total` writable bytes.
    unsafe {
        std::ptr::copy_nonoverlapping(
            (bytes.len() as u64).to_le_bytes().as_ptr(),
            out,
            LENGTH_PREFIX,
        );
        std::ptr::copy_nonoverlapping(bytes.as_ptr(), out.add(LENGTH_PREFIX), bytes.len());
    }
    out
}
