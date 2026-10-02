package dev.testbuild;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ModProjectInstaller {
    private static final String RUNTIME_VERSION = "webmod-runtime-v0.1";
    private static final String TICK_BEGIN = "// WEBMOD-TICK-BEGIN";
    private static final Pattern TICK_METHOD =
            Pattern.compile("\\bpublic\\s+void\\s+tick\\s*\\(\\s*\\)\\s*\\{");

    record Result(Path project, Path minecraftSource, List<Path> writtenFiles, boolean tickHookAdded) {}

    static Result prepare(Path input) throws IOException {
        Path project = input.toAbsolutePath().normalize();
        requireDir(project, "26.2 project");
        requireFile(project.resolve("receipt.json"), "reconstruction receipt");
        requireFile(project.resolve("settings.gradle.kts"), "Gradle settings");
        requireFile(project.resolve("build.gradle.kts"), "root Gradle build");

        Path gameJava = project.resolve("game/src/main/java");
        requireDir(gameJava, "game Java source");
        Path minecraft = gameJava.resolve("net/minecraft/client/Minecraft.java");
        requireFile(minecraft, "Minecraft client source");

        List<Path> written = new ArrayList<>();
        written.add(write(gameJava.resolve("dev/eagler/modding/WebModRuntime.java"), runtimeSource()));
        written.add(write(gameJava.resolve("dev/eagler/modding/generated/GeneratedModEntrypoints.java"), generatedEntrypointsSource()));
        written.add(write(gameJava.resolve("net/fabricmc/api/ModInitializer.java"), modInitializerSource()));
        written.add(write(gameJava.resolve("net/fabricmc/api/ClientModInitializer.java"), clientModInitializerSource()));
        written.add(write(gameJava.resolve("net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.java"), clientTickEventsSource()));

        Path modding = project.resolve("modding");
        Files.createDirectories(modding.resolve("mods"));
        Files.createDirectories(modding.resolve("generated"));
        written.add(write(modding.resolve("README.md"), generatedReadme()));
        written.add(write(modding.resolve(".runtime-version"), RUNTIME_VERSION + "\n"));

        String source = Files.readString(minecraft, StandardCharsets.UTF_8);
        boolean hookAdded = false;
        if (!source.contains(TICK_BEGIN)) {
            String patched = wrapTick(source);
            Files.writeString(minecraft, patched, StandardCharsets.UTF_8);
            hookAdded = true;
        }

        return new Result(project, minecraft, List.copyOf(written), hookAdded);
    }

    static void print(Result result) {
        System.out.println("Mod-ready 26.2 source prepared: " + result.project());
        System.out.println("Runtime files written: " + result.writtenFiles().size());
        System.out.println("Minecraft tick hook: " + (result.tickHookAdded() ? "installed" : "already present"));
        System.out.println("Mods staging directory: " + result.project().resolve("modding/mods"));
        System.out.println("Next: scan-mods <mods-directory>, then add static entrypoint/Mixin compilation.");
    }

    private static String wrapTick(String source) {
        Matcher matcher = TICK_METHOD.matcher(source);
        if (!matcher.find()) {
            throw new HybridBuilder.UserError("could not find public void tick() in Minecraft.java; refusing to guess");
        }
        if (matcher.find()) {
            throw new HybridBuilder.UserError("found multiple public void tick() methods in Minecraft.java; refusing to guess");
        }

        matcher = TICK_METHOD.matcher(source);
        matcher.find();
        int open = source.indexOf('{', matcher.start());
        int close = matchingBrace(source, open);
        if (close < 0) throw new HybridBuilder.UserError("could not find the end of Minecraft.tick()");

        String before = source.substring(0, open + 1);
        String body = source.substring(open + 1, close);
        String after = source.substring(close);

        String start = "\n        " + TICK_BEGIN
                + "\n        dev.eagler.modding.WebModRuntime.clientTickStart(this);"
                + "\n        try {";
        String end = "\n        } finally {"
                + "\n            dev.eagler.modding.WebModRuntime.clientTickEnd(this);"
                + "\n        }"
                + "\n        // WEBMOD-TICK-END\n";
        return before + start + body + end + after;
    }

    private static int matchingBrace(String text, int open) {
        int depth = 0;
        boolean string = false, character = false, lineComment = false, blockComment = false, escape = false;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            char n = i + 1 < text.length() ? text.charAt(i + 1) : 0;

            if (lineComment) {
                if (c == '\n') lineComment = false;
                continue;
            }
            if (blockComment) {
                if (c == '*' && n == '/') { blockComment = false; i++; }
                continue;
            }
            if (string) {
                if (escape) { escape = false; continue; }
                if (c == '\\') { escape = true; continue; }
                if (c == '"') string = false;
                continue;
            }
            if (character) {
                if (escape) { escape = false; continue; }
                if (c == '\\') { escape = true; continue; }
                if (c == '\'') character = false;
                continue;
            }

            if (c == '/' && n == '/') { lineComment = true; i++; continue; }
            if (c == '/' && n == '*') { blockComment = true; i++; continue; }
            if (c == '"') { string = true; continue; }
            if (c == '\'') { character = true; continue; }
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }

    private static Path write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private static void requireFile(Path p, String label) {
        if (!Files.isRegularFile(p)) throw new HybridBuilder.UserError("missing " + label + ": " + p);
    }

    private static void requireDir(Path p, String label) {
        if (!Files.isDirectory(p)) throw new HybridBuilder.UserError("missing " + label + ": " + p);
    }

    private static String runtimeSource() {
        return """
                package dev.eagler.modding;

                import dev.eagler.modding.generated.GeneratedModEntrypoints;
                import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
                import net.minecraft.client.Minecraft;

                public final class WebModRuntime {
                    private static boolean initialized;

                    public static void clientTickStart(Minecraft client) {
                        ensureInitialized();
                        ClientTickEvents.fireStart(client);
                    }

                    public static void clientTickEnd(Minecraft client) {
                        ClientTickEvents.fireEnd(client);
                    }

                    private static void ensureInitialized() {
                        if (initialized) return;
                        initialized = true;
                        GeneratedModEntrypoints.initialize();
                    }

                    private WebModRuntime() {}
                }
                """;
    }

    private static String generatedEntrypointsSource() {
        return """
                package dev.eagler.modding.generated;

                /**
                 * Rewritten by the mod compiler. The empty version keeps a vanilla mod-ready
                 * build valid before any external mod JAR has been imported.
                 */
                public final class GeneratedModEntrypoints {
                    public static void initialize() {
                    }

                    private GeneratedModEntrypoints() {}
                }
                """;
    }

    private static String modInitializerSource() {
        return """
                package net.fabricmc.api;

                public interface ModInitializer {
                    void onInitialize();
                }
                """;
    }

    private static String clientModInitializerSource() {
        return """
                package net.fabricmc.api;

                public interface ClientModInitializer {
                    void onInitializeClient();
                }
                """;
    }

    private static String clientTickEventsSource() {
        return """
                package net.fabricmc.fabric.api.client.event.lifecycle.v1;

                import java.util.ArrayList;
                import java.util.List;
                import java.util.Objects;
                import net.minecraft.client.Minecraft;

                public final class ClientTickEvents {
                    @FunctionalInterface
                    public interface StartTick {
                        void onStartTick(Minecraft client);
                    }

                    @FunctionalInterface
                    public interface EndTick {
                        void onEndTick(Minecraft client);
                    }

                    public static final StartEvent START_CLIENT_TICK = new StartEvent();
                    public static final EndEvent END_CLIENT_TICK = new EndEvent();

                    public static final class StartEvent {
                        private final List<StartTick> listeners = new ArrayList<>();

                        public void register(StartTick listener) {
                            listeners.add(Objects.requireNonNull(listener, "listener"));
                        }

                        private void fire(Minecraft client) {
                            for (StartTick listener : List.copyOf(listeners)) listener.onStartTick(client);
                        }
                    }

                    public static final class EndEvent {
                        private final List<EndTick> listeners = new ArrayList<>();

                        public void register(EndTick listener) {
                            listeners.add(Objects.requireNonNull(listener, "listener"));
                        }

                        private void fire(Minecraft client) {
                            for (EndTick listener : List.copyOf(listeners)) listener.onEndTick(client);
                        }
                    }

                    public static void fireStart(Minecraft client) {
                        START_CLIENT_TICK.fire(client);
                    }

                    public static void fireEnd(Minecraft client) {
                        END_CLIENT_TICK.fire(client);
                    }

                    private ClientTickEvents() {}
                }
                """;
    }

    private static String generatedReadme() {
        return """
                # 26.2 browser mod runtime

                This project has the first source-level modding hooks installed.

                Current working pieces:
                - Minecraft client tick is wrapped with start/end mod-runtime hooks.
                - Fabric ModInitializer and ClientModInitializer API types are present.
                - Fabric ClientTickEvents START_CLIENT_TICK and END_CLIENT_TICK are wired.
                - modding/mods is the staging directory for external JARs.
                - The build helper can identify Fabric, Forge and NeoForge metadata and flag Mixins/native code.

                Not implemented yet:
                - fabric.mod.json entrypoint generation from real JARs.
                - build-time Mixin transformation.
                - the Forge/FML event bus and registry compatibility layer.
                - automatic dependency remapping/resolution.
                - dynamic runtime class loading. Browser mods will be statically compiled into the TeaVM/Wasm build.

                Do not treat a successfully scanned JAR as compatible until the compiler stage accepts it.
                """;
    }

    private ModProjectInstaller() {}
}
