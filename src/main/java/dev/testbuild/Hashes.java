package dev.testbuild;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class Hashes {
    record Fingerprint(long size, String sha256) {}

    static Fingerprint file(Path path) throws IOException {
        MessageDigest d = digest("SHA-256");
        try (var in = Files.newInputStream(path)) {
            byte[] buf = new byte[1024 * 1024];
            for (int n; (n = in.read(buf)) >= 0;) if (n > 0) d.update(buf, 0, n);
        }
        return new Fingerprint(Files.size(path), HexFormat.of().formatHex(d.digest()));
    }

    static String gitBlobSha1(Path path) throws IOException {
        MessageDigest d = digest("SHA-1");
        long size = Files.size(path);
        d.update(("blob " + size + "\0").getBytes(StandardCharsets.UTF_8));
        try (var in = Files.newInputStream(path)) {
            byte[] buf = new byte[1024 * 1024];
            for (int n; (n = in.read(buf)) >= 0;) if (n > 0) d.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(d.digest());
    }

    static String gitBlobSha1(byte[] bytes) {
        MessageDigest d = digest("SHA-1");
        d.update(("blob " + bytes.length + "\0").getBytes(StandardCharsets.UTF_8));
        d.update(bytes);
        return HexFormat.of().formatHex(d.digest());
    }

    private static MessageDigest digest(String name) {
        try { return MessageDigest.getInstance(name); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(name + " unavailable", e); }
    }

    private Hashes() {}
}
