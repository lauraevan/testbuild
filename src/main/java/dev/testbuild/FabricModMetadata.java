package dev.testbuild;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class FabricModMetadata {
    record Data(String id, String version, String name, String environment,
                Map<String, List<String>> entrypoints, List<String> mixins,
                String accessWidener, Map<String, Object> depends) {
        List<String> commonEntrypoints() { return entrypoints.getOrDefault("main", List.of()); }
        List<String> clientEntrypoints() { return entrypoints.getOrDefault("client", List.of()); }
    }

    static Data read(Path jar) throws IOException {
        try (JarFile jf = new JarFile(jar.toFile(), false)) {
            JarEntry entry = jf.getJarEntry("fabric.mod.json");
            if (entry == null) throw new HybridBuilder.UserError("not a Fabric mod (missing fabric.mod.json): " + jar);
            String json;
            try (InputStream in = jf.getInputStream(entry)) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            final Map<String, Object> root;
            try {
                root = MiniJson.object(MiniJson.parse(json));
            } catch (IllegalArgumentException ex) {
                throw new HybridBuilder.UserError("invalid fabric.mod.json in " + jar.getFileName() + ": " + ex.getMessage());
            }

            String id = string(root.get("id"), "id", true);
            String version = string(root.get("version"), "version", false);
            String name = string(root.get("name"), "name", false);
            String environment = string(root.get("environment"), "environment", false);
            if (environment == null) environment = "*";

            Map<String, List<String>> entrypoints = parseEntrypoints(root.get("entrypoints"));
            List<String> mixins = parseMixins(root.get("mixins"));
            String accessWidener = string(root.get("accessWidener"), "accessWidener", false);
            if (accessWidener == null) accessWidener = string(root.get("classTweaker"), "classTweaker", false);

            Map<String, Object> depends = new LinkedHashMap<>();
            Object rawDepends = root.get("depends");
            if (rawDepends instanceof Map<?, ?> map) {
                for (var e : map.entrySet()) {
                    if (e.getKey() instanceof String key) depends.put(key, e.getValue());
                }
            }

            return new Data(id, version, name, environment, Map.copyOf(entrypoints),
                    List.copyOf(mixins), accessWidener, Map.copyOf(depends));
        }
    }

    private static Map<String, List<String>> parseEntrypoints(Object value) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> map)) return out;
        for (var e : map.entrySet()) {
            if (!(e.getKey() instanceof String key)) continue;
            List<String> values = new ArrayList<>();
            Object raw = e.getValue();
            if (raw instanceof List<?> list) {
                for (Object item : list) addEntrypoint(values, item);
            } else {
                addEntrypoint(values, raw);
            }
            out.put(key, List.copyOf(values));
        }
        return out;
    }

    private static void addEntrypoint(List<String> out, Object value) {
        if (value instanceof String s) {
            out.add(s);
        } else if (value instanceof Map<?, ?> map) {
            Object v = map.get("value");
            if (v instanceof String s) out.add(s);
        }
    }

    private static List<String> parseMixins(Object value) {
        List<String> out = new ArrayList<>();
        if (!(value instanceof List<?> list)) return out;
        for (Object item : list) {
            if (item instanceof String s) out.add(s);
            else if (item instanceof Map<?, ?> map) {
                Object config = map.get("config");
                if (config instanceof String s) out.add(s);
            }
        }
        return out;
    }

    private static String string(Object value, String field, boolean required) {
        if (value == null) {
            if (required) throw new HybridBuilder.UserError("fabric.mod.json missing required field: " + field);
            return null;
        }
        if (!(value instanceof String s)) throw new HybridBuilder.UserError("fabric.mod.json field is not a string: " + field);
        return s;
    }

    private FabricModMetadata() {}
}
