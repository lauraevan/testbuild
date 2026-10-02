package dev.testbuild;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class FabricModImporter {
    private static final String GRADLE_BEGIN = "// WEBMOD-CLASSPATH-BEGIN";
    private static final String GRADLE_END = "// WEBMOD-CLASSPATH-END";

    record ImportedMod(Path sourceJar, Path classpathJar, FabricModMetadata.Data metadata,
                       int resourcesCopied, int mixinClasses, int classTweakerDirectives,
                       List<String> blockers) {}

    record Result(Path project, List<ImportedMod> mods, Path entrypointsSource, Path report,
                  List<Path> patchedGradleFiles) {}

    static Result importDirectory(Path projectInput, Path modsInput) throws IOException {
        Path project = projectInput.toAbsolutePath().normalize();
        Path modsDir = modsInput.toAbsolutePath().normalize();
        ModProjectInstaller.prepare(project);

        if (!Files.isDirectory(modsDir)) {
            throw new HybridBuilder.UserError("mods directory does not exist: " + modsDir);
        }

        List<Path> jars;
        try (var stream = Files.list(modsDir)) {
            jars = stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted()
                    .toList();
        }
        if (jars.isEmpty()) throw new HybridBuilder.UserError("no mod JARs found in: " + modsDir);

        Path classpath = project.resolve("modding/classpath");
        Path generated = project.resolve("modding/generated");
        Path reports = project.resolve("modding/reports");
        Files.createDirectories(classpath);
        Files.createDirectories(generated);
        Files.createDirectories(reports);

        List<ImportedMod> imported = new ArrayList<>();
        for (Path jar : jars) {
            ModJarScanner.Report scan = ModJarScanner.inspect(jar);
            if (!scan.loaders().contains(ModJarScanner.Loader.FABRIC)) continue;
            imported.add(importOne(project, jar, scan, classpath, generated));
        }
        if (imported.isEmpty()) {
            throw new HybridBuilder.UserError("no Fabric mods were found in: " + modsDir);
        }

        Path entrypoints = writeEntrypoints(project, imported);
        List<Path> gradleFiles = patchGradle(project, imported);
        Path report = reports.resolve("fabric-import-report.txt");
        Files.writeString(report, report(imported, gradleFiles), StandardCharsets.UTF_8);
        return new Result(project, List.copyOf(imported), entrypoints, report, List.copyOf(gradleFiles));
    }

    static void print(Result result) {
        System.out.println("Imported Fabric mods: " + result.mods().size());
        for (ImportedMod mod : result.mods()) {
            System.out.println("  " + mod.metadata().id()
                    + (mod.metadata().version() == null ? "" : " " + mod.metadata().version())
                    + " -> " + mod.classpathJar().getFileName());
            System.out.println("    resources copied: " + mod.resourcesCopied());
            System.out.println("    mixin classes: " + mod.mixinClasses());
            System.out.println("    class tweaker directives: " + mod.classTweakerDirectives());
            if (!mod.blockers().isEmpty()) {
                System.out.println("    blockers:");
                for (String blocker : mod.blockers()) System.out.println("      - " + blocker);
            }
        }
        System.out.println("Generated entrypoints: " + result.entrypointsSource());
        System.out.println("Compatibility report: " + result.report());
        System.out.println("Gradle modules patched: " + result.patchedGradleFiles().size());
    }

    private static ImportedMod importOne(Path project, Path jar, ModJarScanner.Report scan,
                                         Path classpath, Path generated) throws IOException {
        FabricModMetadata.Data meta = FabricModMetadata.read(jar);
        List<String> blockers = new ArrayList<>();
        if (scan.hasNativeCode()) blockers.add("contains native libraries/JNI payloads");
        if ("server".equals(meta.environment())) blockers.add("server-only mod cannot initialize in the browser client");

        for (String ep : meta.commonEntrypoints()) if (!simpleEntrypoint(ep)) blockers.add("unsupported main entrypoint syntax: " + ep);
        for (String ep : meta.clientEntrypoints()) if (!simpleEntrypoint(ep)) blockers.add("unsupported client entrypoint syntax: " + ep);

        Hashes.Fingerprint hash = Hashes.file(jar);
        String safeId = meta.id().replaceAll("[^A-Za-z0-9_.-]", "_");
        Path staged = classpath.resolve(safeId + "-" + hash.sha256().substring(0, 16) + ".jar");
        Files.copy(jar, staged, StandardCopyOption.REPLACE_EXISTING);

        Path metaDir = generated.resolve(safeId);
        Files.createDirectories(metaDir);

        int resources = 0;
        int mixinClasses = 0;
        int tweakDirectives = 0;
        try (JarFile jf = new JarFile(jar.toFile(), false)) {
            copyEntry(jf, "fabric.mod.json", metaDir.resolve("fabric.mod.json"));

            for (String mixin : meta.mixins()) {
                JarEntry entry = jf.getJarEntry(mixin);
                if (entry == null) {
                    blockers.add("declared Mixin config missing from JAR: " + mixin);
                    continue;
                }
                copyEntry(jf, mixin, metaDir.resolve(Path.of(mixin).getFileName().toString()));
                int count = countMixinClasses(readUtf8(jf, entry));
                mixinClasses += count;
                if (count > 0) blockers.add("requires build-time Mixin transformation: " + mixin + " (" + count + " mixin classes)");
            }

            if (meta.accessWidener() != null) {
                JarEntry entry = jf.getJarEntry(meta.accessWidener());
                if (entry == null) {
                    blockers.add("declared class tweaker/access widener missing from JAR: " + meta.accessWidener());
                } else {
                    String text = readUtf8(jf, entry);
                    Path copy = metaDir.resolve(Path.of(meta.accessWidener()).getFileName().toString());
                    Files.writeString(copy, text, StandardCharsets.UTF_8);
                    var tweaks = analyzeClassTweaker(text);
                    tweakDirectives = tweaks.directives();
                    blockers.addAll(tweaks.blockers());
                }
            }

            var entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!(name.startsWith("assets/") || name.startsWith("data/"))) continue;
                Path destination = project.resolve("game/src/main/resources").resolve(name).normalize();
                Path resourceRoot = project.resolve("game/src/main/resources").normalize();
                if (!destination.startsWith(resourceRoot)) throw new HybridBuilder.UserError("unsafe resource path in " + jar.getFileName() + ": " + name);
                byte[] bytes;
                try (InputStream in = jf.getInputStream(entry)) { bytes = in.readAllBytes(); }
                if (Files.exists(destination)) {
                    byte[] old = Files.readAllBytes(destination);
                    if (!java.util.Arrays.equals(old, bytes)) {
                        throw new HybridBuilder.UserError("resource collision importing " + meta.id() + ": " + name);
                    }
                    continue;
                }
                Files.createDirectories(destination.getParent());
                Files.write(destination, bytes);
                resources++;
            }
        }

        return new ImportedMod(jar, staged, meta, resources, mixinClasses, tweakDirectives, List.copyOf(blockers));
    }

    private static Path writeEntrypoints(Path project, List<ImportedMod> mods) throws IOException {
        Path target = project.resolve("game/src/main/java/dev/eagler/modding/generated/GeneratedModEntrypoints.java");
        StringBuilder common = new StringBuilder();
        StringBuilder client = new StringBuilder();

        for (ImportedMod mod : mods) {
            for (String ep : mod.metadata().commonEntrypoints()) {
                if (simpleEntrypoint(ep)) common.append("        new ").append(ep).append("().onInitialize();\n");
            }
            for (String ep : mod.metadata().clientEntrypoints()) {
                if (simpleEntrypoint(ep)) client.append("        new ").append(ep).append("().onInitializeClient();\n");
            }
        }

        String source = "package dev.eagler.modding.generated;\n\n"
                + "/** Generated by import-fabric. Do not hand-edit; re-run the importer instead. */\n"
                + "public final class GeneratedModEntrypoints {\n"
                + "    public static void initializeCommon() {\n" + common + "    }\n\n"
                + "    public static void initializeClient() {\n" + client + "    }\n\n"
                + "    private GeneratedModEntrypoints() {}\n"
                + "}\n";
        Files.createDirectories(target.getParent());
        Files.writeString(target, source, StandardCharsets.UTF_8);
        return target;
    }

    private static List<Path> patchGradle(Path project, List<ImportedMod> mods) throws IOException {
        List<String> jars = mods.stream()
                .map(mod -> "modding/classpath/" + mod.classpathJar().getFileName())
                .distinct().sorted().toList();

        StringBuilder block = new StringBuilder(GRADLE_BEGIN).append('\n');
        block.append("dependencies {\n");
        for (String jar : jars) {
            block.append("    implementation(files(rootProject.file(\"").append(jar).append("\")))\n");
        }
        block.append("}\n").append(GRADLE_END);

        List<Path> changed = new ArrayList<>();
        for (String module : List.of("game", "target_teavm_wasm_gc", "target_teavm_wasm_gc_server", "target_teavm_wasm_gc_mesh")) {
            Path build = project.resolve(module).resolve("build.gradle.kts");
            if (!Files.isRegularFile(build)) continue;
            String source = Files.readString(build, StandardCharsets.UTF_8);
            String patched = replaceMarker(source, block.toString());
            if (!patched.equals(source)) Files.writeString(build, patched, StandardCharsets.UTF_8);
            changed.add(build);
        }
        if (changed.isEmpty()) {
            throw new HybridBuilder.UserError("could not find module build.gradle.kts files to add imported mod JARs");
        }
        return changed;
    }

    private static String replaceMarker(String source, String block) {
        int start = source.indexOf(GRADLE_BEGIN);
        int end = source.indexOf(GRADLE_END);
        if (start >= 0 && end >= start) {
            end += GRADLE_END.length();
            return source.substring(0, start) + block + source.substring(end);
        }
        if (!source.endsWith("\n")) source += "\n";
        return source + "\n" + block + "\n";
    }

    private static String report(List<ImportedMod> mods, List<Path> gradleFiles) {
        StringBuilder out = new StringBuilder();
        out.append("26.2 Fabric browser import report\n\n");
        for (ImportedMod mod : mods) {
            var meta = mod.metadata();
            out.append(meta.id()).append(' ').append(meta.version() == null ? "(unknown version)" : meta.version()).append('\n');
            out.append("  name: ").append(meta.name()).append('\n');
            out.append("  environment: ").append(meta.environment()).append('\n');
            out.append("  common entrypoints: ").append(meta.commonEntrypoints()).append('\n');
            out.append("  client entrypoints: ").append(meta.clientEntrypoints()).append('\n');
            out.append("  mixin configs: ").append(meta.mixins()).append('\n');
            out.append("  access widener/class tweaker: ").append(meta.accessWidener()).append('\n');
            out.append("  declared dependencies: ").append(meta.depends()).append('\n');
            out.append("  copied resources: ").append(mod.resourcesCopied()).append('\n');
            out.append("  mixin classes requiring transformation: ").append(mod.mixinClasses()).append('\n');
            out.append("  class tweaker directives: ").append(mod.classTweakerDirectives()).append('\n');
            if (mod.blockers().isEmpty()) {
                out.append("  preflight: no known hard blocker in this stage\n");
            } else {
                out.append("  remaining blockers:\n");
                for (String blocker : mod.blockers()) out.append("    - ").append(blocker).append('\n');
            }
            out.append('\n');
        }
        out.append("Gradle classpath patched in:\n");
        for (Path path : gradleFiles) out.append("  - ").append(path).append('\n');
        out.append("\nA clean preflight does not guarantee TeaVM compatibility. The next gate is :game:compileJava / TeaVM link after transformations.\n");
        return out.toString();
    }

    private record TweakerAnalysis(int directives, List<String> blockers) {}

    private static TweakerAnalysis analyzeClassTweaker(String text) {
        int directives = 0;
        List<String> blockers = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("classTweaker ")) continue;
            directives++;
            if (line.startsWith("transitive-extend-enum ") || line.startsWith("extend-enum ")) {
                blockers.add("requires enum extension transform: " + line);
            } else if (line.startsWith("accessible ") || line.startsWith("transitive-accessible ")
                    || line.startsWith("mutable ") || line.startsWith("transitive-mutable ")
                    || line.startsWith("extendable ") || line.startsWith("transitive-extendable ")) {
                blockers.add("requires class tweaker/access transform: " + line);
            } else {
                blockers.add("unsupported class tweaker directive: " + line);
            }
        }
        return new TweakerAnalysis(directives, List.copyOf(blockers));
    }

    private static int countMixinClasses(String json) {
        try {
            Map<String, Object> root = MiniJson.object(MiniJson.parse(json));
            return listSize(root.get("mixins")) + listSize(root.get("client")) + listSize(root.get("server"));
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static int listSize(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    private static boolean simpleEntrypoint(String value) {
        return value.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+");
    }

    private static void copyEntry(JarFile jf, String name, Path destination) throws IOException {
        JarEntry entry = jf.getJarEntry(name);
        if (entry == null) return;
        Files.createDirectories(destination.getParent());
        try (InputStream in = jf.getInputStream(entry)) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String readUtf8(JarFile jf, JarEntry entry) throws IOException {
        try (InputStream in = jf.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private FabricModImporter() {}
}
