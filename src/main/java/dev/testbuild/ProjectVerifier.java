package dev.testbuild;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

final class ProjectVerifier {
    record Inspection(Path project, Path web, Hashes.Fingerprint receipt, Hashes.Fingerprint assets,
                      Hashes.Fingerprint classesWasm, Hashes.Fingerprint meshWasm, Hashes.Fingerprint serverWasm) {}

    static Inspection inspect(Path input) throws IOException {
        Path project = input.toAbsolutePath().normalize();
        requireDir(project, "26.2 project");
        Path receipt = project.resolve("receipt.json");
        requireFile(receipt, "reconstruction receipt");
        String json = Files.readString(receipt, StandardCharsets.UTF_8);
        field(json, "tool", Pins.TOOL);
        field(json, "official_client_jar_sha256", Pins.OFFICIAL_JAR_SHA256);
        field(json, "patch_bundle_sha256", Pins.PATCH_BUNDLE_SHA256);
        field(json, "final_manifest_sha256", Pins.FINAL_MANIFEST_SHA256);
        integer(json, "final_java_file_count", Pins.FINAL_JAVA_FILES);

        requireFile(project.resolve("wasm-toolchain/build-single-html.js"), "standalone packager");
        Path web = project.resolve("target_teavm_wasm_gc/build/web");
        Path assets = web.resolve("assets.epk"), classes = web.resolve("classes.wasm");
        Path mesh = web.resolve("mesh-worker.wasm"), server = web.resolve("server-worker.wasm");
        requireFile(assets, "26.2 assets.epk"); requireFile(classes, "classes.wasm");
        requireFile(mesh, "mesh-worker.wasm"); requireFile(server, "server-worker.wasm");
        requireFile(web.resolve("index.html"), "web index");
        return new Inspection(project, web, Hashes.file(receipt), Hashes.file(assets), Hashes.file(classes),
                Hashes.file(mesh), Hashes.file(server));
    }

    static void print(Inspection i) {
        System.out.println("26.2 project verified: " + i.project());
        System.out.println("receipt.json        " + i.receipt().sha256());
        System.out.println("classes.wasm        " + i.classesWasm().sha256());
        System.out.println("mesh-worker.wasm    " + i.meshWasm().sha256());
        System.out.println("server-worker.wasm  " + i.serverWasm().sha256());
        System.out.println("assets.epk (26.2)   " + i.assets().sha256());
    }

    private static void field(String json, String key, String expected) {
        var m = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json);
        if (!m.find() || !expected.equals(m.group(1))) throw new HybridBuilder.UserError("receipt mismatch: " + key);
    }

    private static void integer(String json, String key, int expected) {
        var m = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        if (!m.find() || Integer.parseInt(m.group(1)) != expected) throw new HybridBuilder.UserError("receipt mismatch: " + key);
    }

    private static void requireFile(Path p, String label) { if (!Files.isRegularFile(p)) throw new HybridBuilder.UserError("missing " + label + ": " + p); }
    private static void requireDir(Path p, String label) { if (!Files.isDirectory(p)) throw new HybridBuilder.UserError("missing " + label + ": " + p); }
    private ProjectVerifier() {}
}
