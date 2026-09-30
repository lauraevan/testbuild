# testbuild: 26.3 asset-swap experiment

This repo tests one specific theory: **can the 26.3 backport run on the released 26.2 Java/WASM codebase when only `assets.epk` is changed?**

The 26.2 base is the released Eaglercraft 26.2 u1 patcher/toolchain source from Radicle:

- RID: `rad:z2BWVCwcwTyoQ2veMJLb1eFpMtJDj`
- pinned head: `016a49a92ab4f43db18b892ab7929b62c0e96dba`
- release: `u1`
- exact mirror fallback: `lauraevan/scode`

The 26.3 asset archive is pinned to mcjs commit `bc586558628e2d7ef8ea1cebf198495625619eb2` and Git blob `9a0c1dd65b0ff91c57c177c1f053140c7eb74d61`.

## Java-first tooling

The experiment logic is now Java, not shell. With Java 17 or newer you can compile it directly without Gradle:

```text
javac --release 17 -d build/tool src/main/java/dev/testbuild/*.java
java -cp build/tool dev.testbuild.HybridBuilder --help
```

### 1. Prepare the exact 26.2 source-tool base

```text
java -cp build/tool dev.testbuild.HybridBuilder setup-base work/26.2-base
```

It attempts the Radicle seed first and falls back to the exact GitHub mirror if the seed's smart-HTTP clone is unavailable. Either way it refuses to continue unless `HEAD` is exactly the pinned Radicle commit.

### 2. Fetch the exact 26.3 EPK

```text
java -cp build/tool dev.testbuild.HybridBuilder fetch-assets work/eag26.3-assets.epk
```

The download is checked against the expected Git blob SHA-1 before it is accepted, then its SHA-256 is printed.

### 3. Build/reconstruct 26.2

Use the released 26.2 patcher to reconstruct the editable Java project and build its normal TeaVM/WASM web output. The upstream release is source-only and intentionally omits several required authorized inputs; see `docs/BUILD_26_2.md`.

### 4. Verify the built 26.2 project

```text
java -cp build/tool dev.testbuild.HybridBuilder inspect work/26.2-project
```

The verifier requires the pinned 26.2 reconstruction receipt, including:

- official 26.2 JAR SHA-256
- pinned source-patch bundle SHA-256
- 7,142 final Java files
- pinned final patched-source manifest SHA-256
- built `classes.wasm`
- built mesh/server worker WASMs
- the original 26.2 `assets.epk`

### 5. Package the hybrid

```text
java -cp build/tool dev.testbuild.HybridBuilder package \
  work/26.2-project \
  work/eag26.3-assets.epk \
  work/eaglercraft-26.3-asset-swap.html
```

The Java builder:

1. validates the 26.2 source-build receipt;
2. hashes the 26.2 main/mesh/server WASMs;
3. temporarily replaces only `target_teavm_wasm_gc/build/web/assets.epk`;
4. calls the released packager with `--skip-build`, so no Java or WASM is rebuilt;
5. restores the original 26.2 EPK in a `finally` block even if packaging fails;
6. writes an `asset-swap-receipt.json` containing the exact hashes used in the experiment.

That makes the result a clean **26.2 code + 26.3 assets** test rather than an ambiguous mixed rebuild.

## Build the helper normally

A small Gradle project is also included for IDE use and conventional builds. The helper itself has no third-party Java dependencies.

```text
gradle selfTest
gradle run --args="inspect /path/to/built-26.2-project"
```
