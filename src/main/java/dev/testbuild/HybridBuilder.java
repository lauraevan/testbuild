package dev.testbuild;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HybridBuilder {
    public static void main(String[] args) {
        try {
            if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) { usage(); return; }
            switch (args[0]) {
                case "fetch-setup" -> {
                    count(args, 1, 2);
                    SetupFetch.fetch(args.length == 2 ? Path.of(args[1]) : Path.of(Pins.SETUP_NAME));
                }
                case "install-setup" -> {
                    count(args, 3, 3);
                    SetupInstall.install(Path.of(args[1]), Path.of(args[2]));
                }
                case "setup-base" -> { count(args, 2, 2); BaseSetup.setup(Path.of(args[1])); }
                case "fetch-assets" -> { count(args, 1, 2); AssetFetch.fetch(args.length == 2 ? Path.of(args[1]) : Path.of("eag26.3-assets.epk")); }
                case "inspect" -> { count(args, 2, 2); ProjectVerifier.print(ProjectVerifier.inspect(Path.of(args[1]))); }
                case "package" -> {
                    count(args, 3, 4);
                    Path project = Path.of(args[1]);
                    Path output = args.length == 4 ? Path.of(args[3]) : project.resolve("eaglercraft-26.3-asset-swap.html");
                    printResult(HybridPackager.pack(project, Path.of(args[2]), output));
                }
                case "package-26.3" -> {
                    count(args, 2, 3);
                    Path project = Path.of(args[1]).toAbsolutePath().normalize();
                    Path cache = project.resolve(".testbuild/eag26.3-assets.epk");
                    if (Files.exists(cache)) AssetFetch.verify(cache); else AssetFetch.fetch(cache);
                    Path output = args.length == 3 ? Path.of(args[2]) : project.resolve("eaglercraft-26.3-asset-swap.html");
                    printResult(HybridPackager.pack(project, cache, output));
                }
                case "restore" -> { count(args, 2, 2); HybridPackager.restore(Path.of(args[1])); }
                default -> throw new UserError("unknown command: " + args[0]);
            }
        } catch (UserError | IOException | InterruptedException e) {
            System.err.println("ERROR: " + e.getMessage()); System.exit(2);
        }
    }

    private static void printResult(HybridPackager.Result r) {
        System.out.println("Hybrid HTML: " + r.output());
        System.out.println("Receipt: " + r.receipt());
        System.out.println("SHA-256: " + r.sha256());
    }

    private static void usage() {
        System.out.println("testbuild 26.3 asset-swap builder\nUsage:");
        System.out.println("  fetch-setup [Eaglercraft-26.2-u1-Setup.jar]");
        System.out.println("  install-setup <Setup.jar> <new-or-verified-kit-directory>");
        System.out.println("  setup-base <source-directory>");
        System.out.println("  fetch-assets [output.epk]");
        System.out.println("  inspect <built-26.2-project>");
        System.out.println("  package <built-26.2-project> <26.3-assets.epk> [output.html]");
        System.out.println("  package-26.3 <built-26.2-project> [output.html]");
        System.out.println("  restore <built-26.2-project>");
        System.out.println("\npackage-26.3 fetches/verifies the pinned modified 26.3 EPK, swaps only assets.epk,");
        System.out.println("packages with --skip-build, then restores the original 26.2 assets.");
    }

    private static void count(String[] a, int min, int max) { if (a.length < min || a.length > max) throw new UserError("wrong arguments; use --help"); }
    static final class UserError extends RuntimeException { UserError(String m) { super(m); } }
    private HybridBuilder() {}
}
