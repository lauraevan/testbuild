# 26.3 Asset-Swap Test Build

Experimental hybrid build using the **mcjs 26.2 WASM/runtime** with the **mcjs 26.3 asset pack**.

## What this tests

The runtime remains 26.2:
- decoder WASM: 26.2
- main compressed WASM: 26.2
- mesh worker WASM: 26.2
- server worker WASM: 26.2
- sounds EPK: 26.2
- loader: 26.2

Only the asset pack is swapped.

The file `eag26.2-assets.epk` in this repo intentionally contains the bytes from mcjs `wasm-loader/26.3/eag26.3-assets.epk`. It keeps the 26.2 filename so the stock 26.2 loader consumes the 26.3 asset pack without additional loader changes.

## Goal

Boot the hybrid and record what succeeds or fails. Anything that works is compatible with the 26.2 runtime as asset/data content. Failures involving missing registries, methods, IDs, serialization, rendering behavior, or gameplay logic point to changes that must live in code/WASM rather than the EPK.

## Provenance

Baseline and experimental asset pack were copied from `Enchantment-Niko/mcjs` on 2026-09-30.
