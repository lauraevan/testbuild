# Building the pinned 26.2 base

The Radicle project is the **26.2 u1 source reconstruction/build tooling**, not a complete redistributed game checkout.

Pinned source:

- RID: `rad:z2BWVCwcwTyoQ2veMJLb1eFpMtJDj`
- commit: `016a49a92ab4f43db18b892ab7929b62c0e96dba`

The release's own README says it intentionally omits the generated/decompiled game source and several external inputs. That is why `testbuild` does not pretend those files came from the Radicle repo.

## Required upstream inputs

The released Java patcher expects authorized copies of:

- official Minecraft 26.2 client JAR
- Vineflower 1.12.0
- Java 17 for source reconstruction
- the pinned source patch bundle
- the pinned project skeleton for a full Gradle/TeaVM workspace
- the pinned resource overlay and its external resources for the reviewed resource tree
- Java 25, Node and npm for the browser build
- authorized sounds/music EPK inputs for standalone packaging

Important pinned identities embedded by the released patcher:

```text
official 26.2 JAR SHA-256
40896ee9f1e2bec3c934daac7e93d41e9e3d9c2f8ae0ca366d52ffbfd1afa290

source patch bundle SHA-256
df3af583c06aa22748d21f039980cdd3923dbc7ab0bc21accbbb28b1cd1e7389

project skeleton SHA-256
e76f606630ce6596061e7ac5a76d01a541846cac7d8d1424ec38a942ab00c071

resource overlay SHA-256
2ba7e3376891c64f8bf57f3687e05b8dbe1971a75475b6825449e5e5f96d71f3

final patched Java manifest SHA-256
3afd3f5a3ddafedc8fcd2bef51828f86f8d0228588a33e393548887ff5cb3d59

final patched Java files
7142
```

## Why the experiment packages with `--skip-build`

The released `wasm-toolchain/build-single-html.js` normally rebuilds source-matched `assets.epk` as part of a full build. For this experiment we first complete the normal 26.2 build, then temporarily replace the already-built web `assets.epk` and invoke:

```text
node wasm-toolchain/build-single-html.js --skip-build --output <hybrid.html>
```

That path reads the existing `classes.wasm`, `mesh-worker.wasm`, `server-worker.wasm`, runtime JS and EPK files and packages them without relinking the game. The Java helper automatically restores the original 26.2 asset archive afterward.
