package dev.testbuild;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ClassTweakerApplier {
    record Result(int directives, int applied, List<String> unresolved, List<String> enumExtensions,
                  List<Path> changedFiles) {}

    static Result apply(Path project, String text) throws IOException {
        Path javaRoot = project.resolve("game/src/main/java");
        Map<Path, String> sources = new LinkedHashMap<>();
        List<String> unresolved = new ArrayList<>();
        List<String> enumExtensions = new ArrayList<>();
        int directives = 0;
        int applied = 0;

        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("classTweaker ")) continue;
            directives++;

            String normalized = line;
            if (normalized.startsWith("transitive-")) normalized = normalized.substring("transitive-".length());
            String[] parts = normalized.split("\\s+");
            if (parts.length < 3) {
                unresolved.add("malformed directive: " + line);
                continue;
            }

            if ("extend-enum".equals(parts[0])) {
                enumExtensions.add(line);
                continue;
            }

            String action = parts[0];
            String kind = parts[1];
            String internalName = parts[2];
            SourceTarget target = target(javaRoot, internalName);
            if (!Files.isRegularFile(target.file())) {
                unresolved.add("missing source for: " + line);
                continue;
            }
            String source = sources.computeIfAbsent(target.file(), p -> {
                try { return Files.readString(p, StandardCharsets.UTF_8); }
                catch (IOException e) { throw new ReadFailure(e); }
            });

            Patch patch;
            if ("class".equals(kind)) {
                if (!"accessible".equals(action)) {
                    unresolved.add("unsupported class directive: " + line);
                    continue;
                }
                patch = makeClassAccessible(source, target.simpleName());
            } else if ("field".equals(kind) && parts.length >= 4) {
                String field = parts[3];
                patch = switch (action) {
                    case "accessible" -> patchField(source, field, true, false);
                    case "mutable" -> patchField(source, field, false, true);
                    default -> Patch.fail("unsupported field action");
                };
            } else if ("method".equals(kind) && parts.length >= 4) {
                String method = parts[3];
                if ("<init>".equals(method)) method = target.simpleName();
                patch = switch (action) {
                    case "accessible" -> patchMethod(source, method, true, false);
                    case "extendable" -> patchMethod(source, method, true, true);
                    default -> Patch.fail("unsupported method action");
                };
            } else {
                unresolved.add("unsupported directive: " + line);
                continue;
            }

            if (!patch.changed()) {
                unresolved.add(line + " (" + patch.reason() + ")");
                continue;
            }
            sources.put(target.file(), patch.source());
            applied++;
        }

        List<Path> changed = new ArrayList<>();
        for (var e : sources.entrySet()) {
            String old = Files.readString(e.getKey(), StandardCharsets.UTF_8);
            if (!old.equals(e.getValue())) {
                Files.writeString(e.getKey(), e.getValue(), StandardCharsets.UTF_8);
                changed.add(e.getKey());
            }
        }

        return new Result(directives, applied, List.copyOf(unresolved),
                List.copyOf(enumExtensions), List.copyOf(changed));
    }

    private record SourceTarget(Path file, String simpleName) {}

    private static SourceTarget target(Path root, String internalName) {
        String outer = internalName;
        String nested = null;
        int dollar = internalName.indexOf('$');
        if (dollar >= 0) {
            outer = internalName.substring(0, dollar);
            nested = internalName.substring(dollar + 1);
            int next = nested.lastIndexOf('$');
            if (next >= 0) nested = nested.substring(next + 1);
        }
        String topSimple = outer.substring(outer.lastIndexOf('/') + 1);
        String simple = nested == null ? topSimple : nested;
        return new SourceTarget(root.resolve(outer + ".java"), simple);
    }

    private record Patch(boolean changed, String source, String reason) {
        static Patch fail(String reason) { return new Patch(false, null, reason); }
        static Patch ok(String source) { return new Patch(true, source, ""); }
    }

    private static Patch makeClassAccessible(String source, String simpleName) {
        Pattern p = Pattern.compile("(?m)^([^\\n]*\\b(?:class|interface|record|enum)\\s+"
                + Pattern.quote(simpleName) + "\\b[^\\n]*)$");
        Matcher m = p.matcher(source);
        List<int[]> matches = new ArrayList<>();
        while (m.find()) matches.add(new int[]{m.start(1), m.end(1)});
        if (matches.size() != 1) return Patch.fail("class declaration match count=" + matches.size());
        int[] range = matches.get(0);
        String line = source.substring(range[0], range[1]);
        String changed = publicize(line, false);
        if (changed.equals(line)) return Patch.fail("class already accessible or declaration not recognized");
        return Patch.ok(source.substring(0, range[0]) + changed + source.substring(range[1]));
    }

    private static Patch patchField(String source, String field, boolean accessible, boolean mutable) {
        Pattern name = Pattern.compile("\\b" + Pattern.quote(field) + "\\b");
        List<int[]> candidates = new ArrayList<>();
        int offset = 0;
        for (String line : source.split("\\n", -1)) {
            Matcher m = name.matcher(line);
            if (m.find()) {
                String before = line.substring(0, m.start());
                String after = line.substring(m.end());
                boolean declarationShape = !before.contains("(")
                        && (after.stripLeading().startsWith("=")
                        || after.stripLeading().startsWith(";")
                        || after.stripLeading().startsWith(","));
                if (declarationShape) candidates.add(new int[]{offset, offset + line.length()});
            }
            offset += line.length() + 1;
        }
        if (candidates.size() != 1) return Patch.fail("field declaration match count=" + candidates.size());
        int[] range = candidates.get(0);
        String line = source.substring(range[0], range[1]);
        String changed = line;
        if (accessible) changed = publicize(changed, false);
        if (mutable) changed = removeWord(changed, "final");
        if (changed.equals(line)) return Patch.fail("field declaration already satisfied or not recognized");
        return Patch.ok(source.substring(0, range[0]) + changed + source.substring(range[1]));
    }

    private static Patch patchMethod(String source, String method, boolean accessible, boolean extendable) {
        Pattern name = Pattern.compile("\\b" + Pattern.quote(method) + "\\s*\\(");
        List<int[]> candidates = new ArrayList<>();
        int offset = 0;
        for (String line : source.split("\\n", -1)) {
            Matcher m = name.matcher(line);
            if (m.find()) {
                String before = line.substring(0, m.start());
                String trimmed = before.stripLeading();
                boolean declarationShape = !trimmed.startsWith("//")
                        && !before.endsWith(".")
                        && !before.contains("=")
                        && !before.contains("->")
                        && !trimmed.startsWith("return ");
                if (declarationShape) candidates.add(new int[]{offset, offset + line.length()});
            }
            offset += line.length() + 1;
        }
        if (candidates.size() != 1) return Patch.fail("method declaration match count=" + candidates.size());
        int[] range = candidates.get(0);
        String line = source.substring(range[0], range[1]);
        String changed = line;
        if (accessible) changed = publicize(changed, false);
        if (extendable) {
            changed = removeWord(changed, "final");
            changed = removeWord(changed, "private");
        }
        if (changed.equals(line)) return Patch.fail("method declaration already satisfied or not recognized");
        return Patch.ok(source.substring(0, range[0]) + changed + source.substring(range[1]));
    }

    private static String publicize(String line, boolean allowPackageOnly) {
        String changed = removeWord(removeWord(line, "private"), "protected");
        if (containsWord(changed, "public")) return changed;
        int indent = 0;
        while (indent < changed.length() && Character.isWhitespace(changed.charAt(indent))) indent++;
        if (indent >= changed.length()) return line;
        return changed.substring(0, indent) + "public " + changed.substring(indent);
    }

    private static String removeWord(String line, String word) {
        return line.replaceFirst("\\b" + Pattern.quote(word) + "\\s+", "");
    }

    private static boolean containsWord(String line, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(line).find();
    }

    private static final class ReadFailure extends RuntimeException {
        ReadFailure(IOException cause) { super(cause); }
    }

    private ClassTweakerApplier() {}
}
