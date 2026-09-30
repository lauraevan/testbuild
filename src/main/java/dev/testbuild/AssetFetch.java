package dev.testbuild;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

final class AssetFetch {
    static Hashes.Fingerprint fetch(Path input) throws IOException, InterruptedException {
        Path out = input.toAbsolutePath().normalize();
        if (Files.exists(out)) throw new HybridBuilder.UserError("output already exists: " + out);
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpResponse<byte[]> r = client.send(HttpRequest.newBuilder(URI.create(Pins.ASSET_URL)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (r.statusCode() != 200) throw new HybridBuilder.UserError("asset download HTTP " + r.statusCode());
        byte[] bytes = r.body();
        if (bytes.length < 1024) throw new HybridBuilder.UserError("downloaded EPK is implausibly small");
        String blob = Hashes.gitBlobSha1(bytes);
        if (!Pins.ASSET_BLOB_SHA1.equals(blob)) throw new HybridBuilder.UserError("asset blob mismatch: " + blob);
        Files.write(out, bytes);
        return verify(out);
    }

    static Hashes.Fingerprint verify(Path input) throws IOException {
        Path path = input.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new HybridBuilder.UserError("missing 26.3 EPK: " + path);
        if (Files.size(path) < 1024) throw new HybridBuilder.UserError("26.3 EPK is implausibly small: " + path);
        String blob = Hashes.gitBlobSha1(path);
        if (!Pins.ASSET_BLOB_SHA1.equals(blob)) throw new HybridBuilder.UserError("26.3 EPK blob mismatch: " + blob);
        Hashes.Fingerprint fp = Hashes.file(path);
        System.out.println("26.3 assets: " + path);
        System.out.println("Git blob SHA-1: " + blob);
        System.out.println("SHA-256: " + fp.sha256());
        System.out.println("Bytes: " + fp.size());
        return fp;
    }

    private AssetFetch() {}
}
