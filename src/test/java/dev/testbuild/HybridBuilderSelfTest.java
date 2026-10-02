package dev.testbuild;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

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

            Files.writeString(project.resolve("settings.gradle.kts"), "rootProject.name = \"fake\"\n");
            Files.writeString(project.resolve("build.gradle.kts"), "plugins { java }\n");
            var mc = project.resolve("game/src/main/java/net/minecraft/client/Minecraft.java");
            Files.createDirectories(mc.getParent());
            Files.writeString(mc, """
                    package net.minecraft.client;
                    public final class Minecraft {
                        public void tick() {
                            int x = 1;
                            if (x == 2) return;
                        }
                    }
                    """);

            var modded = ModProjectInstaller.prepare(project);
            check(modded.tickHookAdded(), "tick hook added");
            String patchedMinecraft = Files.readString(mc);
            check(patchedMinecraft.contains("// WEBMOD-TICK-BEGIN"), "tick hook marker");
            check(patchedMinecraft.contains("clientTickEnd(this)"), "tick end hook");
            check(Files.isRegularFile(project.resolve("game/src/main/java/net/fabricmc/api/ModInitializer.java")), "fabric initializer API");
            check(!ModProjectInstaller.prepare(project).tickHookAdded(), "tick hook idempotent");

            var mods = temp.resolve("mods");
            Files.createDirectories(mods);
            var fabricJar = mods.resolve("demo-fabric.jar");
            try (var jar = new JarOutputStream(Files.newOutputStream(fabricJar))) {
                put(jar, "fabric.mod.json", "{\"id\":\"demo\"}");
                put(jar, "demo/Demo.class", new byte[]{0, 1, 2});
                put(jar, "demo.mixins.json", "{}");
            }
            var reports = ModJarScanner.scanDirectory(mods);
            check(reports.size() == 1, "mod scanner count");
            check(reports.get(0).loaders().contains(ModJarScanner.Loader.FABRIC), "fabric detection");
            check(reports.get(0).hasMixins(), "mixin detection");
            check(!reports.get(0).hasNativeCode(), "native detection");

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

    private static void put(JarOutputStream jar, String name, String value) throws Exception {
        put(jar, name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void put(JarOutputStream jar, String name, byte[] value) throws Exception {
        jar.putNextEntry(new JarEntry(name));
        jar.write(value);
        jar.closeEntry();
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError("failed: " + name);
    }
}
