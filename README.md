# 26.3 Asset-Swap Test Build

This repo is a controlled hybrid experiment:

- **loader/runtime:** mcjs 26.2
- **main WASM:** mcjs 26.2
- **mesh/server worker WASMs:** mcjs 26.2
- **sounds:** mcjs 26.2
- **assets.epk:** mcjs 26.3

The loader is pinned to mcjs commit `bc586558628e2d7ef8ea1cebf198495625619eb2`.

## Why the binaries are remote

The large binary files are fetched directly from the pinned public mcjs commit instead of being duplicated into this repo. That keeps this test small and makes the one intentional difference obvious: the 26.2 loader requests the **26.3 asset pack** for its asset slot.

## What this tells us

If the game boots and 26.3 content appears correctly, those parts are compatible with the existing 26.2 engine and are likely asset/data-driven.

Anything that fails with missing registries, IDs, methods, serialization, rendering behavior, networking, or gameplay logic is evidence that additional 26.3 changes live in code/WASM and cannot be reproduced by swapping `assets.epk` alone.

## Entry point

Open or deploy `index.html`.
