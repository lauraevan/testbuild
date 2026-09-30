package dev.testbuild;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class BaseSetup {
    static void setup(Path input) throws IOException, InterruptedException {
        Path dir = input.toAbsolutePath().normalize();
        if (!Files.exists(dir.resolve(".git"))) {
            if (Files.exists(dir) && !empty(dir)) throw new HybridBuilder.UserError("base directory is not empty: " + dir);
            if (dir.getParent() != null) Files.createDirectories(dir.getParent());
            if (run(null, "git", "clone", Pins.RADICLE_CLONE, dir.toString()) != 0) {
                if (Files.exists(dir)) deleteTree(dir);
                throw new HybridBuilder.UserError("could not clone the pinned Radicle 26.2 source; use fetch-setup for the complete release package");
            }
        }
        if (run(dir, "git", "checkout", "--detach", Pins.RADICLE_HEAD) != 0)
            throw new HybridBuilder.UserError("could not checkout pinned head " + Pins.RADICLE_HEAD);
        String head = capture(dir, "git", "rev-parse", "HEAD").trim();
        if (!Pins.RADICLE_HEAD.equals(head)) throw new HybridBuilder.UserError("base mismatch: " + head);
        System.out.println("26.2 source base ready: " + dir);
        System.out.println("Radicle RID: " + Pins.RADICLE_RID);
        System.out.println("Release: " + Pins.RADICLE_RELEASE_ID);
        System.out.println("Commit: " + head);
    }

    private static boolean empty(Path p) throws IOException {
        if (!Files.exists(p)) return true;
        if (!Files.isDirectory(p)) return false;
        try (var s = Files.list(p)) { return s.findAny().isEmpty(); }
    }

    private static int run(Path dir, String... cmd) throws IOException, InterruptedException {
        ProcessBuilder p = new ProcessBuilder(cmd); if (dir != null) p.directory(dir.toFile());
        p.inheritIO(); return p.start().waitFor();
    }

    private static String capture(Path dir, String... cmd) throws IOException, InterruptedException {
        ProcessBuilder p = new ProcessBuilder(cmd); p.directory(dir.toFile()); p.redirectErrorStream(true);
        Process child = p.start();
        String out = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (child.waitFor() != 0) throw new HybridBuilder.UserError("command failed: " + String.join(" ", cmd));
        return out;
    }

    private static void deleteTree(Path root) throws IOException {
        try (var s = Files.walk(root)) {
            for (Path p : s.sorted((a,b) -> b.getNameCount() - a.getNameCount()).toList()) Files.deleteIfExists(p);
        }
    }

    private BaseSetup() {}
}
