package dev.testbuild;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class SetupInstall {
    static void install(Path setupInput, Path destinationInput) throws IOException, InterruptedException {
        Path setup = setupInput.toAbsolutePath().normalize();
        Path destination = destinationInput.toAbsolutePath().normalize();
        SetupFetch.verify(setup);
        Path parent = destination.getParent();
        if (parent == null || !Files.isDirectory(parent))
            throw new HybridBuilder.UserError("installation parent must already exist: " + parent);

        String javaName = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", javaName);
        if (!Files.isRegularFile(java)) throw new HybridBuilder.UserError("current Java executable is unavailable: " + java);

        ProcessBuilder pb = new ProcessBuilder(java.toString(), "-jar", setup.toString(),
                "--install-dir", destination.toString());
        pb.inheritIO();
        int exit = pb.start().waitFor();
        if (exit != 0) throw new HybridBuilder.UserError("Setup extraction exited with status " + exit);

        require(destination.resolve("eaglercraft-26.2-u1-patcher-gui.jar"), "GUI JAR");
        require(destination.resolve("eaglercraft-26.2-java-cli.jar"), "CLI JAR");
        require(destination.resolve("inputs/source-patch-bundle.zip"), "source patch bundle");
        require(destination.resolve("inputs/project-skeleton-v5-teavm-runtime-verified.zip"), "project skeleton");
        require(destination.resolve("inputs/resource-overlay-normal.zip"), "resource overlay");
        System.out.println("Complete 26.2 kit installed and verified at: " + destination);
    }

    private static void require(Path p, String label) {
        if (!Files.isRegularFile(p)) throw new HybridBuilder.UserError("installed kit is missing " + label + ": " + p);
    }

    private SetupInstall() {}
}
