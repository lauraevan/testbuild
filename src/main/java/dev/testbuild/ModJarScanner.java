package dev.testbuild;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class ModJarScanner {
    enum Loader { FABRIC, FORGE, NEOFORGE, UNKNOWN }

    record Report(Path jar, EnumSet<Loader> loaders, int classFiles, int resourceFiles,
                  List<String> metadataEntries, List<String> mixinConfigs, List<String> nativeEntries) {
        boolean hasNativeCode() { return !nativeEntries.isEmpty(); }
        boolean hasMixins() { return !mixinConfigs.isEmpty(); }
    }

    static List<Report> scanDirectory(Path input) throws IOException {
        Path dir = input.toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) throw new HybridBuilder.UserError("mods directory does not exist: " + dir);
        List<Path> jars;
        try (var stream = Files.list(dir)) {
            jars = stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted().toList();
        }
        List<Report> out = new ArrayList<>(jars.size());
        for (Path jar : jars) out.add(inspect(jar));
        return Collections.unmodifiableList(out);
    }

    static Report inspect(Path input) throws IOException {
        Path jar = input.toAbsolutePath().normalize();
        if (!Files.isRegularFile(jar)) throw new HybridBuilder.UserError("mod JAR does not exist: " + jar);

        EnumSet<Loader> loaders = EnumSet.noneOf(Loader.class);
        List<String> metadata = new ArrayList<>();
        List<String> mixins = new ArrayList<>();
        List<String> nativeEntries = new ArrayList<>();
        int classes = 0, resources = 0;

        try (JarFile jf = new JarFile(jar.toFile(), false)) {
            var entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                String lower = name.toLowerCase(Locale.ROOT);

                if (name.equals("fabric.mod.json")) { loaders.add(Loader.FABRIC); metadata.add(name); }
                else if (name.equals("META-INF/mods.toml")) { loaders.add(Loader.FORGE); metadata.add(name); }
                else if (name.equals("META-INF/neoforge.mods.toml")) { loaders.add(Loader.NEOFORGE); metadata.add(name); }
                else if (name.equals("META-INF/MANIFEST.MF")) metadata.add(name);

                if (lower.endsWith(".class")) classes++; else resources++;
                if (lower.endsWith(".json") && lower.contains("mixin")) mixins.add(name);
                if (lower.endsWith(".dll") || lower.endsWith(".so") || lower.endsWith(".dylib") || lower.endsWith(".jnilib")) {
                    nativeEntries.add(name);
                }
            }
        }

        if (loaders.isEmpty()) loaders.add(Loader.UNKNOWN);
        return new Report(jar, loaders, classes, resources,
                List.copyOf(metadata), List.copyOf(mixins), List.copyOf(nativeEntries));
    }

    static void print(List<Report> reports) {
        if (reports.isEmpty()) { System.out.println("No mod JARs found."); return; }
        for (Report r : reports) {
            System.out.println(r.jar().getFileName());
            System.out.println("  loader: " + r.loaders());
            System.out.println("  classes/resources: " + r.classFiles() + "/" + r.resourceFiles());
            if (!r.metadataEntries().isEmpty()) System.out.println("  metadata: " + String.join(", ", r.metadataEntries()));
            if (r.hasMixins()) System.out.println("  mixins: " + r.mixinConfigs().size() + " (requires build-time Mixin transform)");
            if (r.hasNativeCode()) System.out.println("  native code: " + r.nativeEntries().size() + " (not browser-compatible unchanged)");
        }
    }

    private ModJarScanner() {}
}
