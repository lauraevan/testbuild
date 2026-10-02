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
    private static final String RUNTIME_VERSION = "webmod-runtime-v0.2";
    private static final String TICK_BEGIN = "// WEBMOD-TICK-BEGIN";
    private static final String CONSTRUCTOR_BEGIN = "// WEBMOD-CONSTRUCTOR-BEGIN";
    private static final Pattern TICK_METHOD =
            Pattern.compile("\\bpublic\\s+void\\s+tick\\s*\\(\\s*\\)\\s*\\{");
    private static final Pattern MINECRAFT_CONSTRUCTOR =
            Pattern.compile("\\bpublic\\s+Minecraft\\s*\\(");

    record Result(Path project, Path minecraftSource, List<Path> writtenFiles,
                  boolean constructorHookAdded, boolean tickHookAdded) {}

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

        Path generatedEntrypoints = gameJava.resolve("dev/eagler/modding/generated/GeneratedModEntrypoints.java");
        if (!Files.isRegularFile(generatedEntrypoints)) {
            written.add(write(generatedEntrypoints, generatedEntrypointsSource()));
        }

        written.add(write(gameJava.resolve("net/fabricmc/api/ModInitializer.java"), modInitializerSource()));
        written.add(write(gameJava.resolve("net/fabricmc/api/ClientModInitializer.java"), clientModInitializerSource()));
        written.addAll(FabricCompatInstaller.install(gameJava));

        Path modding = project.resolve("modding");
        Files.createDirectories(modding.resolve("mods"));
        Files.createDirectories(modding.resolve("classpath"));
        Files.createDirectories(modding.resolve("generated"));
        Files.createDirectories(modding.resolve("reports"));
        written.add(write(modding.resolve("README.md"), generatedReadme()));
        written.add(write(modding.resolve(".runtime-version"), RUNTIME_VERSION + "\n"));

        String source = Files.readString(minecraft, StandardCharsets.UTF_8);
        boolean constructorAdded = false;
        if (!source.contains(CONSTRUCTOR_BEGIN)) {
            source = hookConstructor(source);
            constructorAdded = true;
        }

        boolean tickAdded = false;
        if (!source.contains(TICK_BEGIN)) {
            source = wrapTick(source);
            tickAdded = true;
        }

        if (constructorAdded || tickAdded) {
            Files.writeString(minecraft, source, StandardCharsets.UTF_8);
        }

        return new Result(project, minecraft, List.copyOf(written), constructorAdded, tickAdded);
    }

    static void print(Result result) {
        System.out.println("Mod-ready 26.2 source prepared: " + result.project());
        System.out.println("Runtime/API files written: " + result.writtenFiles().size());
        System.out.println("Minecraft constructor hook: " + (result.constructorHookAdded() ? "installed" : "already present"));
        System.out.println("Minecraft tick hook: " + (result.tickHookAdded() ? "installed" : "already present"));
        System.out.println("Mods staging directory: " + result.project().resolve("modding/mods"));
        System.out.println("Next: import-fabric <project> <mods-directory>");
    }

    private static String hookConstructor(String source) {
        Matcher matcher = MINECRAFT_CONSTRUCTOR.matcher(source);
        int matchStart = -1;
        int paren = -1;
        int matches = 0;
        while (matcher.find()) {
            matches++;
            matchStart = matcher.start();
            paren = source.indexOf('(', matcher.start());
        }
        if (matches == 0) throw new HybridBuilder.UserError("could not find public Minecraft(...) constructor; refusing to guess");
        if (matches > 1) throw new HybridBuilder.UserError("found multiple public Minecraft(...) constructors; refusing to guess");

        int closeParen = matchingDelimiter(source, paren, '(', ')');
        if (closeParen < 0) throw new HybridBuilder.UserError("could not parse Minecraft constructor parameters");

        int open = source.indexOf('{', closeParen);
        if (open < 0) throw new HybridBuilder.UserError("could not find Minecraft constructor body");
        int close = matchingBrace(source, open);
        if (close < 0) throw new HybridBuilder.UserError("could not find end of Minecraft constructor");

        String begin = "\n        " + CONSTRUCTOR_BEGIN
                + "\n        dev.eagler.modding.WebModRuntime.bootstrapCommon();"
                + "\n        // WEBMOD-CONSTRUCTOR-COMMON-END\n";
        String end = "\n        // WEBMOD-CONSTRUCTOR-CLIENT-BEGIN"
                + "\n        dev.eagler.modding.WebModRuntime.bootstrapClient();"
                + "\n        // WEBMOD-CONSTRUCTOR-END\n";

        return source.substring(0, open + 1) + begin
                + source.substring(open + 1, close) + end
                + source.substring(close);
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

    private static int matchingDelimiter(String text, int open, char left, char right) {
        int depth = 0;
        boolean string = false, character = false, lineComment = false, blockComment = false, escape = false;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            char n = i + 1 < text.length() ? text.charAt(i + 1) : 0;

            if (lineComment) { if (c == '\n') lineComment = false; continue; }
            if (blockComment) { if (c == '*' && n == '/') { blockComment = false; i++; } continue; }
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
            if (c == left) depth++;
            else if (c == right && --depth == 0) return i;
        }
        return -1;
    }

    private static int matchingBrace(String text, int open) {
        return matchingDelimiter(text, open, '{', '}');
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
                    private static boolean commonInitialized;
                    private static boolean clientInitialized;

                    public static void bootstrapCommon() {
                        if (commonInitialized) return;
                        commonInitialized = true;
                        GeneratedModEntrypoints.initializeCommon();
                    }

                    public static void bootstrapClient() {
                        if (clientInitialized) return;
                        bootstrapCommon();
                        clientInitialized = true;
                        GeneratedModEntrypoints.initializeClient();
                    }

                    public static void clientTickStart(Minecraft client) {
                        bootstrapClient();
                        ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(client);
                    }

                    public static void clientTickEnd(Minecraft client) {
                        ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(client);
                    }

                    private WebModRuntime() {}
                }
                """;
    }

    private static String generatedEntrypointsSource() {
        return """
                package dev.eagler.modding.generated;

                /**
                 * Rewritten by import-fabric. The empty version keeps the browser client
                 * buildable before any external mod JARs are imported.
                 */
                public final class GeneratedModEntrypoints {
                    public static void initializeCommon() {
                    }

                    public static void initializeClient() {
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

    private static String generatedReadme() {
        return """
                # 26.2 browser mod runtime

                This generated project contains the browser mod compatibility layer.

                Current pipeline:
                - source-level common/client bootstrap hooks in Minecraft's constructor;
                - client tick start/end events;
                - Fabric metadata/entrypoint import support;
                - staged mod JAR classpath support;
                - mod assets/data resource import with collision checks;
                - a focused Fabric API compatibility slice for Farmer's Delight startup;
                - compatibility reporting for Mixins, class tweakers and native binaries.

                The browser build is ahead-of-time compiled. JARs are imported before TeaVM/Wasm-GC;
                there is no JVM class loader inside the browser.
                """;
    }

    private ModProjectInstaller() {}
}
