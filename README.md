# testbuild: 26.3 asset-swap experiment

This repo tests whether the 26.3 backport can run on the released 26.2 Java/WASM codebase when only `assets.epk` is changed.

## 26.2 base: complete u1 Setup release

The experiment now targets the newer complete Radicle release:

- RID: `rad:z2BWVCwcwTyoQ2veMJLb1eFpMtJDj`
- release ID: `36fcf6e983b7326e9f1cb47c1796f8f5d385e99f`
- source commit: `24d9c4d0737477e74182ff73b4c2be0e47f5bf5e`
- artifact: `Eaglercraft-26.2-u1-Setup.jar`
- Setup SHA-256: `57bfcacdf24310d48f462a9a508fe2a59acad8487a8de34bb40963c46183c1bd`

Unlike the older source-only release, this Setup JAR includes the source-patch bundle, project skeleton, resource overlay, resources, Vineflower and bundled audio used by the Normal build flow. You still supply your official Minecraft 26.2 client JAR. Java/build tools are installed or downloaded by Setup as needed.

The modified 26.3 asset archive is pinned to mcjs commit `bc586558628e2d7ef8ea1cebf198495625619eb2` and Git blob `9a0c1dd65b0ff91c57c177c1f053140c7eb74d61`.

## Build the helper

```text
javac --release 17 -d build/tool src/main/java/dev/testbuild/*.java
java -cp build/tool dev.testbuild.HybridBuilder --help
```

## 1. Fetch the exact complete 26.2 Setup

```text
java -cp build/tool dev.testbuild.HybridBuilder fetch-setup work/Eaglercraft-26.2-u1-Setup.jar
```

The helper refuses the download unless it is exactly 77,804,659 bytes and matches the release SHA-256.

Run Setup with Java 17+ and use it to create/build a normal 26.2 project from your official 26.2 client JAR:

```text
java -jar work/Eaglercraft-26.2-u1-Setup.jar
```

## 2. Verify the built 26.2 project

```text
java -cp build/tool dev.testbuild.HybridBuilder inspect work/26.2-project
```

The verifier checks the new complete-release identities, including:

- official 26.2 client JAR SHA-256
- source bundle `fd944e...`
- project skeleton `3656a8...`
- resource overlay identity
- 7,142 patched Java files
- final source manifest `a59247...`
- built main, mesh and server WASMs
- original 26.2 `assets.epk`

## 3. Make the modified-EPK build

The easy path is now:

```text
java -cp build/tool dev.testbuild.HybridBuilder package-26.3 \
  work/26.2-project \
  work/eaglercraft-26.3-asset-swap.html
```

`package-26.3` automatically downloads Niko's pinned modified 26.3 `assets.epk` into the project cache, verifies its Git blob identity, then:

1. records the hashes of the genuine 26.2 `classes.wasm`, mesh worker and server worker;
2. backs up the original 26.2 `assets.epk`;
3. installs the modified 26.3 EPK into the existing web output;
4. invokes `wasm-toolchain/build-single-html.js --skip-build`, so the 26.2 code/WASM is not relinked;
5. restores the original 26.2 EPK even if packaging fails;
6. writes an `.asset-swap-receipt.json` containing the exact inputs and output SHA-256.

The resulting HTML is therefore a controlled **new complete-release 26.2 codebase + modified 26.3 assets** experiment.

The lower-level `fetch-assets` and `package` commands remain available when you want to supply the EPK path manually.
