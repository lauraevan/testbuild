# 26.3 Asset-Swap Test Build

Experimental hybrid build using the mcjs 26.2 WASM/runtime with the mcjs 26.3 asset pack.

## Purpose

This repo isolates one question: how much of the 26.3 backport works when only `assets.epk` is changed while keeping the 26.2 runtime intact?

## Layout

The root loader is the 26.2 WASM loader. Runtime binaries and sounds come from mcjs 26.2. The file named `eag26.2-assets.epk` intentionally contains the 26.3 asset pack so the unmodified 26.2 loader consumes it.

This is an experiment, not yet proof that every 26.3 gameplay change is asset-only.
