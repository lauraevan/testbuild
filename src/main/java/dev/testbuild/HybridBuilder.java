package dev.testbuild;

import java.io.IOException;
import java.nio.file.Path;

public final class HybridBuilder {
    public static void main(String[] args) {
        try {
            if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) { usage(); return; }
            switch (args[0]) {
                case "setup-base" -> { count(args, 2, 2); BaseSetup.setup(Path.of(args[1])); }
                case "fetch-assets" -> { count(args, 1, 2); AssetFetch.fetch(args.length == 2 ? Path.of(args[1]) : Path.of("eag26.3-assets.epk")); }
                case "inspect" -> { count(args, 2, 2); ProjectVerifier.print(ProjectVerifier.inspect(Path.of(args[1]))); }
                case "package" -> {
                    count(args, 3, 4);
                    Path project = Path.of(args[1]);
                    Path output = args.length == 4 ? Path.of(args[3]) : project.resolve("eaglercraft-26.3-asset-swap.html");
                    var r = HybridPackager.pack(project, Path.of(args[2]), output);
                    System.out.println("Hybrid HTML: " + r.output());
                    System.out.println("Receipt: " + r.receipt());
                    System.out.println("SHA-256: " + r.sha256());
                }
                case "restore" -> { count(args, 2, 2); HybridPackager.restore(Path.of(args[1])); }
                default -> throw new UserError("unknown command: " + args[0]);
            }
        } catch (UserError | IOException | InterruptedException e) {
            System.err.println("ERROR: " + e.getMessage()); System.exit(2);
        }
    }

    private static void usage() {
        System.out.println("testbuild 26.3 asset-swap builder\nUsage:");
        System.out.println("  setup-base <directory>");
        System.out.println("  fetch-assets [output.epk]");
        System.out.println("  inspect <built-26.2-project>");
        System.out.println("  package <built-26.2-project> <26.3-assets.epk> [output.html]");
        System.out.println("  restore <built-26.2-project>");
        System.out.println("\npackage temporarily swaps only assets.epk, packages with --skip-build, then restores 26.2 assets.");
    }

    private static void count(String[] a, int min, int max) { if (a.length < min || a.length > max) throw new UserError("wrong arguments; use --help"); }
    static final class UserError extends RuntimeException { UserError(String m) { super(m); } }
    private HybridBuilder() {}
}
