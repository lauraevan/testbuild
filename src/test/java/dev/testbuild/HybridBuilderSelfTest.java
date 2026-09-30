package dev.testbuild;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class HybridBuilderSelfTest {
    public static void main(String[] args) throws Exception {
        var temp = Files.createTempDirectory("testbuild-selftest-");
        try {
            var f = temp.resolve("abc");
            Files.writeString(f, "abc", StandardCharsets.UTF_8);
            check(Hashes.file(f).sha256().equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), "sha256");
            check(Hashes.gitBlobSha1("abc".getBytes(StandardCharsets.UTF_8)).equals("f2ba8f84ab5c1bce84a7b441cb1959cfc7093b7f"), "git blob");

            var project = temp.resolve("project");
            var web = project.resolve("target_teavm_wasm_gc/build/web");
            Files.createDirectories(web);
            Files.createDirectories(project.resolve("wasm-toolchain"));
            Files.writeString(project.resolve("wasm-toolchain/build-single-html.js"), "// packager");
            Files.writeString(web.resolve("assets.epk"), "26.2-assets");
            Files.writeString(web.resolve("classes.wasm"), "classes");
            Files.writeString(web.resolve("mesh-worker.wasm"), "mesh");
            Files.writeString(web.resolve("server-worker.wasm"), "server");
            Files.writeString(web.resolve("index.html"), "<html></html>");
            String receipt = "{\n"
                    + "  \"tool\": \"" + Pins.TOOL + "\",\n"
                    + "  \"official_client_jar_sha256\": \"" + Pins.OFFICIAL_JAR_SHA256 + "\",\n"
                    + "  \"patch_bundle_sha256\": \"" + Pins.PATCH_BUNDLE_SHA256 + "\",\n"
                    + "  \"project_skeleton_sha256\": \"" + Pins.PROJECT_SKELETON_SHA256 + "\",\n"
                    + "  \"resource_overlay_sha256\": \"" + Pins.RESOURCE_OVERLAY_SHA256 + "\",\n"
                    + "  \"final_manifest_sha256\": \"" + Pins.FINAL_MANIFEST_SHA256 + "\",\n"
                    + "  \"final_java_file_count\": " + Pins.FINAL_JAVA_FILES + "\n} \n";
            Files.writeString(project.resolve("receipt.json"), receipt);
            var inspected = ProjectVerifier.inspect(project);
            check(inspected.assets().size() == 11, "project verifier");

            Files.writeString(web.resolve("assets.epk.testbuild-original"), "original");
            Files.writeString(web.resolve("assets.epk"), "replacement");
            HybridPackager.restore(project);
            check(Files.readString(web.resolve("assets.epk")).equals("original"), "asset restore");
            check(!Files.exists(web.resolve("assets.epk.testbuild-original")), "backup removed");

            Files.writeString(project.resolve("receipt.json"), receipt.replace(String.valueOf(Pins.FINAL_JAVA_FILES), "1"));
            boolean rejected = false;
            try { ProjectVerifier.inspect(project); }
            catch (HybridBuilder.UserError expected) { rejected = true; }
            check(rejected, "bad receipt rejection");

            System.out.println("HybridBuilderSelfTest: PASS");
        } finally {
            try (var s = Files.walk(temp)) {
                for (var p : s.sorted((a,b) -> b.getNameCount() - a.getNameCount()).toList()) Files.deleteIfExists(p);
            }
        }
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError("failed: " + name);
    }
}
