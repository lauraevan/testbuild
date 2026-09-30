# 26.3 asset-swap experiment

This test now uses the released **Eaglercraft 26.2 u1 patcher/toolchain source** as its 26.2 base, not a precompiled mcjs 26.2 runtime.

## Pinned 26.2 base

- Radicle RID: `rad:z2BWVCwcwTyoQ2veMJLb1eFpMtJDj`
- Radicle seed: `radicle.jarg.io`
- Pinned head: `016a49a92ab4f43db18b892ab7929b62c0e96dba`
- Release: `u1`
- Exact GitHub mirror: `lauraevan/scode`

The upstream project identifies itself as the Java build tools for Eaglercraft 26.2 u1. Its patcher reconstructs the official 26.2 Java source, applies the pinned source patch bundle, and can create the Gradle/TeaVM workspace used for browser builds.

## Experiment

The intended test is deliberately narrow:

1. Reconstruct/build a normal 26.2 project from the pinned released source tools.
2. Keep the resulting 26.2 Java/WASM unchanged.
3. Replace only `target_teavm_wasm_gc/build/web/assets.epk` with Niko's pinned 26.3 `assets.epk`.
4. Repackage with `node wasm-toolchain/build-single-html.js --skip-build`.

That gives us a clean answer to whether the 26.3 backport can ride on the 26.2 codebase with an asset/data swap.

## Scripts

- `scripts/setup-base.sh` clones and verifies the exact Radicle base, with `lauraevan/scode` as an exact mirror fallback.
- `scripts/build-patcher.sh` builds the released Java patcher.
- `scripts/reconstruct-26.2.sh` reconstructs the editable 26.2 source project from authorized inputs.
- `scripts/fetch-26.3-assets.sh` fetches Niko's 26.3 asset archive from pinned mcjs commit `bc586558`.
- `scripts/swap-26.3-assets.sh` swaps only the EPK in an already-built 26.2 web output and repackages without rebuilding WASM.

## Upstream input limitation

The Radicle release is source-only. It explicitly omits the official 26.2 client JAR, the pinned source-patch bundle, project skeleton archive, resource overlay, game assets, sound/media packs and generated game source. The release's `u1` entry also has no downloadable artifacts.

Those omitted authorized inputs are therefore still required before the full 26.2 source build can run. They are not fabricated or substituted here.
