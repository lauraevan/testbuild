# Forge + Fabric browser modding work

This branch turns the complete Eaglercraft 26.2 u1 source-build flow into the base for a statically compiled browser mod client.

## Why this source is the right base

The pinned Setup release reconstructs the patched 26.2 Java project before TeaVM/Wasm-GC compilation. That gives the mod layer a real Java source tree under `game/src/main/java`, instead of trying to modify an already-linked `classes.wasm`.

Browser builds cannot use the desktop loader model unchanged. Forge/Fabric normally discover and transform classes while running on a JVM. This project moves those steps before TeaVM so the final browser build already contains the selected mods and transformations.

## Implemented on `forge-fabric-web`

Build the helper:

```sh
javac --release 17 -d build/tool src/main/java/dev/testbuild/*.java
```

Prepare a generated 26.2 source project:

```sh
java -cp build/tool dev.testbuild.HybridBuilder prepare-modded work/26.2-project
```

The installer:

- requires a generated project with `receipt.json` and the expected Java source layout;
- wraps `net.minecraft.client.Minecraft.tick()` in start/end runtime hooks;
- preserves returns and exceptions with `try/finally`;
- is idempotent and refuses to guess when the tick method cannot be uniquely located;
- installs a small shared browser mod runtime;
- installs Fabric `ModInitializer` and `ClientModInitializer` API types;
- wires Fabric `ClientTickEvents.START_CLIENT_TICK` and `END_CLIENT_TICK`;
- creates `modding/mods` for staged JARs.

Scan JARs without executing them:

```sh
java -cp build/tool dev.testbuild.HybridBuilder scan-mods work/26.2-project/modding/mods
```

The scanner recognizes:

- `fabric.mod.json`;
- `META-INF/mods.toml`;
- `META-INF/neoforge.mods.toml`;
- Mixin configuration JSON files;
- native libraries that cannot run unchanged in a browser.

## Next implementation stages

1. **Static Fabric entrypoints**
   Parse `fabric.mod.json`, resolve common/client entrypoints, place mod JARs on the compile/TeaVM classpath and generate direct calls in `GeneratedModEntrypoints`.

2. **Build-time Mixins**
   Apply Sponge Mixin transformations to the Minecraft/mod bytecode before TeaVM. No runtime class-loader transformation is required in the browser.

3. **Fabric API coverage**
   Port registry, lifecycle, networking, resource and rendering APIs in compatibility slices. Keep each slice backed by the same internal browser runtime.

4. **Forge/FML compatibility**
   Implement the Forge event buses and mod lifecycle first, then registries/deferred registers and commonly used client hooks. Forge calls should bridge to the same internal events used by Fabric.

5. **Forge transformation support**
   Process access transformers and supported bytecode transformations before TeaVM. Reject unsupported runtime agents/native/JNI features with a clear compatibility report.

6. **Modpack compiler**
   Resolve dependencies, run transformations, generate entrypoints, compile one deterministic Wasm build and cache it by the selected mod hashes.

## Compatibility rule

A JAR being detected does not mean it is compatible. The eventual compiler should only mark a mod compatible after its metadata, dependencies, transformations, Java API use and TeaVM reachability all pass.
