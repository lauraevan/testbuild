package dev.testbuild;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

final class HybridPackager {
    record Result(Path output, Path receipt, String sha256) {}

    static Result pack(Path projectInput, Path replacementInput, Path outputInput) throws IOException, InterruptedException {
        var i = ProjectVerifier.inspect(projectInput);
        Path replacement = replacementInput.toAbsolutePath().normalize();
        if (!Files.isRegularFile(replacement)) throw new HybridBuilder.UserError("missing replacement EPK: " + replacement);
        var repl = Hashes.file(replacement);
        String blob = Hashes.gitBlobSha1(replacement);
        if (!Pins.ASSET_BLOB_SHA1.equals(blob)) throw new HybridBuilder.UserError("replacement is not the pinned 26.3 EPK: " + blob);
        if (repl.size() < 1024) throw new HybridBuilder.UserError("replacement EPK is implausibly small");
        if (repl.sha256().equals(i.assets().sha256())) throw new HybridBuilder.UserError("replacement equals 26.2 assets");

        Path output = outputInput.toAbsolutePath().normalize();
        if (Files.exists(output)) throw new HybridBuilder.UserError("output already exists: " + output);
        if (output.getParent() != null) Files.createDirectories(output.getParent());
        Path live = i.web().resolve("assets.epk"), backup = i.web().resolve("assets.epk.testbuild-original");
        if (Files.exists(backup)) throw new HybridBuilder.UserError("safety backup exists; run restore first");

        Files.copy(live, backup, StandardCopyOption.COPY_ATTRIBUTES);
        try {
            Files.copy(replacement, live, StandardCopyOption.REPLACE_EXISTING);
            if (!Hashes.file(live).sha256().equals(repl.sha256())) throw new HybridBuilder.UserError("EPK copy verification failed");
            ProcessBuilder pb = new ProcessBuilder("node", "wasm-toolchain/build-single-html.js", "--skip-build", "--output", output.toString());
            pb.directory(i.project().toFile()).inheritIO();
            int exit = pb.start().waitFor();
            if (exit != 0) throw new HybridBuilder.UserError("packager exited with status " + exit);
            if (!Files.isRegularFile(output)) throw new HybridBuilder.UserError("packager produced no HTML: " + output);
            var html = Hashes.file(output);
            Path receipt = output.resolveSibling(output.getFileName() + ".asset-swap-receipt.json");
            Files.writeString(receipt, receipt(i, replacement, repl, output, html), StandardCharsets.UTF_8);
            return new Result(output, receipt, html.sha256());
        } finally {
            if (Files.exists(backup)) Files.move(backup, live, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void restore(Path projectInput) throws IOException {
        Path web = projectInput.toAbsolutePath().normalize().resolve("target_teavm_wasm_gc/build/web");
        Path live = web.resolve("assets.epk"), backup = web.resolve("assets.epk.testbuild-original");
        if (!Files.exists(backup)) { System.out.println("No safety backup; nothing to restore."); return; }
        Files.move(backup, live, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Restored original 26.2 assets.epk");
    }

    private static String receipt(ProjectVerifier.Inspection i, Path replacement, Hashes.Fingerprint repl,
                                  Path output, Hashes.Fingerprint html) {
        return "{\n"
                + line("format", "testbuild-26.3-asset-swap-v1")
                + line("created_at", Instant.now().toString())
                + line("radicle_rid", Pins.RADICLE_RID) + line("radicle_head", Pins.RADICLE_HEAD)
                + line("mcjs_26_3_asset_commit", Pins.MCJS_COMMIT)
                + line("project_receipt_sha256", i.receipt().sha256())
                + line("classes_wasm_sha256", i.classesWasm().sha256())
                + line("mesh_worker_wasm_sha256", i.meshWasm().sha256())
                + line("server_worker_wasm_sha256", i.serverWasm().sha256())
                + line("original_26_2_assets_epk_sha256", i.assets().sha256())
                + line("replacement_26_3_assets_epk", replacement.toString())
                + line("replacement_26_3_assets_epk_sha256", repl.sha256())
                + line("packaging_mode", "build-single-html.js --skip-build")
                + line("output", output.toString())
                + "  \"output_sha256\":\"" + html.sha256() + "\",\n"
                + "  \"output_size\":" + html.size() + "\n}\n";
    }

    private static String line(String k, String v) { return "  \"" + esc(k) + "\":\"" + esc(v) + "\",\n"; }
    private static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    private HybridPackager() {}
}
