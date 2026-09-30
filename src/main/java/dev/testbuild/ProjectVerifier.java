package dev.testbuild;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class ProjectVerifier {
    record Inspection(Path project, Path web, Hashes.Fingerprint receipt, Hashes.Fingerprint assets,
                      Hashes.Fingerprint classesWasm, Hashes.Fingerprint meshWasm, Hashes.Fingerprint serverWasm) {}

    static Inspection inspect(Path input) throws IOException {
        Path project = input.toAbsolutePath().normalize();
        requireDir(project, "26.2 project");
        Path receipt = project.resolve("receipt.json");
        requireFile(receipt, "reconstruction receipt");
        String json = Files.readString(receipt, StandardCharsets.UTF_8);
        String compact = json.chars().filter(c -> !Character.isWhitespace(c))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        String q = Character.toString(34);
        expect(compact, q + "tool" + q + ":" + q + Pins.TOOL + q, "tool");
        expect(compact, q + "official_client_jar_sha256" + q + ":" + q + Pins.OFFICIAL_JAR_SHA256 + q, "official JAR");
        expect(compact, q + "patch_bundle_sha256" + q + ":" + q + Pins.PATCH_BUNDLE_SHA256 + q, "patch bundle");
        expect(compact, q + "final_manifest_sha256" + q + ":" + q + Pins.FINAL_MANIFEST_SHA256 + q, "final manifest");
        expect(compact, q + "final_java_file_count" + q + ":" + Pins.FINAL_JAVA_FILES, "final Java file count");

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
        System.out.println("receipt.json       " + i.receipt().sha256());
        System.out.println("classes.wasm       " + i.classesWasm().sha256());
        System.out.println("mesh-worker.wasm   " + i.meshWasm().sha256());
        System.out.println("server-worker.wasm " + i.serverWasm().sha256());
        System.out.println("assets.epk (26.2)  " + i.assets().sha256());
    }

    private static void expect(String compact, String token, String label) {
        if (!compact.contains(token)) throw new HybridBuilder.UserError("receipt mismatch: " + label);
    }
    private static void requireFile(Path p, String label) { if (!Files.isRegularFile(p)) throw new HybridBuilder.UserError("missing " + label + ": " + p); }
    private static void requireDir(Path p, String label) { if (!Files.isDirectory(p)) throw new HybridBuilder.UserError("missing " + label + ": " + p); }
    private ProjectVerifier() {}
}
