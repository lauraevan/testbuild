# Building the current pinned 26.2 base

Current Radicle release:

- RID: `rad:z2BWVCwcwTyoQ2veMJLb1eFpMtJDj`
- release ID: `36fcf6e983b7326e9f1cb47c1796f8f5d385e99f`
- source commit: `24d9c4d0737477e74182ff73b4c2be0e47f5bf5e`
- Setup artifact: `Eaglercraft-26.2-u1-Setup.jar`
- Setup SHA-256: `57bfcacdf24310d48f462a9a508fe2a59acad8487a8de34bb40963c46183c1bd`
- Setup size: 77,804,659 bytes

The release describes this as the **complete Normal patcher setup**. The Setup JAR contains the internal source patches, project skeleton, resource overlay, resources and audio used by the GUI workflow. The official Minecraft 26.2 client JAR is still user-supplied.

Important identities in this release:

```text
official 26.2 JAR SHA-256
40896ee9f1e2bec3c934daac7e93d41e9e3d9c2f8ae0ca366d52ffbfd1afa290

source patch bundle SHA-256
fd944e9cabbebbf4bddce8a39e1b233b3c7d0cd4f860f9cae37550a3fcfa8b02

project skeleton SHA-256
3656a83ed8187e2d859612c566f3aa73e242bf1e4633990c5bb4929e0a846c94

resource overlay SHA-256
2ba7e3376891c64f8bf57f3687e05b8dbe1971a75475b6825449e5e5f96d71f3

final patched Java manifest SHA-256
a5924750314f5de9316e9f67b28ce1decee31e27d67b260760b8176cd269243a

final patched Java files
7142
```

## Asset-swap packaging

Finish a normal 26.2 build first. Its multi-file output lives at:

```text
target_teavm_wasm_gc/build/web
```

The experiment deliberately leaves its compiled 26.2 WASMs alone. It temporarily replaces only `assets.epk` with the pinned modified 26.3 archive and packages using:

```text
node wasm-toolchain/build-single-html.js --skip-build --output <hybrid.html>
```

The Java helper handles the backup, swap, hash receipt and restoration automatically through `package-26.3`.
