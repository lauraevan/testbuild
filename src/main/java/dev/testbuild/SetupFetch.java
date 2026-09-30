package dev.testbuild;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class SetupFetch {
    static Hashes.Fingerprint fetch(Path input) throws IOException, InterruptedException {
        Path out = input.toAbsolutePath().normalize();
        if (Files.exists(out)) throw new HybridBuilder.UserError("output already exists: " + out);
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Path temp = out.resolveSibling(out.getFileName() + ".part");
        Files.deleteIfExists(temp);
        try {
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<Path> r = client.send(
                    HttpRequest.newBuilder(URI.create(Pins.SETUP_URL)).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(temp));
            if (r.statusCode() != 200) throw new HybridBuilder.UserError("Setup download HTTP " + r.statusCode());
            Hashes.Fingerprint fp = verify(temp);
            Files.move(temp, out, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("26.2 complete Setup: " + out);
            System.out.println("Run with Java 17+: java -jar " + out);
            return fp;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static Hashes.Fingerprint verify(Path input) throws IOException {
        Path path = input.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new HybridBuilder.UserError("missing Setup JAR: " + path);
        Hashes.Fingerprint fp = Hashes.file(path);
        if (fp.size() != Pins.SETUP_SIZE) throw new HybridBuilder.UserError(
                "Setup size mismatch: expected " + Pins.SETUP_SIZE + ", got " + fp.size());
        if (!Pins.SETUP_SHA256.equals(fp.sha256())) throw new HybridBuilder.UserError(
                "Setup SHA-256 mismatch: " + fp.sha256());
        System.out.println("Setup verified: " + path);
        System.out.println("SHA-256: " + fp.sha256());
        System.out.println("Bytes: " + fp.size());
        return fp;
    }

    private SetupFetch() {}
}
