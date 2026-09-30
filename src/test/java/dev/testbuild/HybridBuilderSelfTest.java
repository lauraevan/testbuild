package dev.testbuild;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class HybridBuilderSelfTest {
    public static void main(String[] args) throws Exception {
        var temp = Files.createTempDirectory("testbuild-selftest-");
        try {
            var f = temp.resolve("abc"); Files.writeString(f, "abc", StandardCharsets.UTF_8);
            check(Hashes.file(f).sha256().equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), "sha256");
            check(Hashes.gitBlobSha1("abc".getBytes(StandardCharsets.UTF_8)).equals("f2ba8f84ab5c1bce84a7b441cb1959cfc7093b7f"), "git blob");
            System.out.println("HybridBuilderSelfTest: PASS");
        } finally {
            try (var s = Files.walk(temp)) { for (var p : s.sorted((a,b) -> b.getNameCount()-a.getNameCount()).toList()) Files.deleteIfExists(p); }
        }
    }
    private static void check(boolean ok, String n) { if (!ok) throw new AssertionError(n); }
}
