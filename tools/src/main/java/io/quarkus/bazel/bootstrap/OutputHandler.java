package io.quarkus.bazel.bootstrap;

import io.quarkus.bootstrap.app.AugmentResult;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * Handles the output from Quarkus augmentation.
 *
 * QuarkusBootstrap outputs to a directory structure:
 *   quarkus-app/
 *   ├── app/
 *   │   └── application.jar
 *   ├── lib/
 *   │   └── *.jar
 *   ├── quarkus/
 *   │   └── generated-bytecode.jar
 *   └── quarkus-run.jar
 *
 * This handler copies/moves the output to Bazel's expected location.
 */
public class OutputHandler {

    /**
     * Move augmentation output to final destination.
     */
    public static void copyOutput(AugmentResult result, AugmentationConfig config) throws IOException {
        Path sourceDir = result.getJar().getPath().getParent();
        Path targetDir = config.getOutputDir();

        // Ensure target directory exists
        Files.createDirectories(targetDir);

        // Quarkus always writes into <targetDirectory>/quarkus-app/, while Bazel declares
        // <targetDirectory> itself as the tree artifact, so the tree has to be lifted one level.
        if (Files.isSameFile(sourceDir, targetDir)) {
            // Defensive: a future Quarkus/config change could write straight into targetDir.
            // Relocating then would rename every entry onto itself.
            System.out.println("  Output already at target: " + targetDir);
        } else {
            System.out.println("  Moving output from: " + sourceDir);
            System.out.println("  Moving output to:   " + targetDir);
            relocateDirectoryContents(sourceDir, targetDir);
        }

        // Ensure lib/boot/ has quarkus-bootstrap-runner.jar
        ensureBootstrapRunner(config, targetDir);

        System.out.println("  Output moved successfully");
    }

    /**
     * Move every top-level entry of {@code source} into {@code target}, then drop the
     * now-empty {@code source} directory.
     *
     * sourceDir is a child of targetDir, so both are always on the same filesystem and each
     * entry can be renamed rather than copied. Rename is metadata-only: cost does not scale
     * with the size of the dependency JARs under lib/main/. It turns one copy plus one unlink
     * per file into one rename per top-level entry and one rmdir, roughly halving the bytes
     * written by the output stage (Quarkus already wrote the tree once).
     *
     * Deleting source last also covers what the previous copy-based version cleaned up by
     * hand: the leftover quarkus-app/ subtree, whose empty lib/boot/ makes img.exe reject
     * the layer. After the moves that subtree holds nothing, so plain deletion suffices.
     */
    private static void relocateDirectoryContents(Path source, Path target) throws IOException {
        List<Path> entries;
        try (var stream = Files.list(source)) {
            entries = stream.toList();
        }

        for (Path entry : entries) {
            Path dest = target.resolve(entry.getFileName());
            try {
                Files.move(entry, dest);
            } catch (FileAlreadyExistsException | DirectoryNotEmptyException
                    | AtomicMoveNotSupportedException e) {
                // Rename cannot serve two cases: something already sits at dest (stale,
                // non-sandboxed output dir) or the filesystem refuses the move. Both are
                // rare, so fall back to the old copy-then-delete behaviour per entry.
                System.out.println("    Falling back to copy for " + entry.getFileName() + ": " + e);
                copyDirectory(entry, dest);
                deleteDirectory(entry);
            }
        }

        deleteDirectory(source);
    }

    /**
     * Quarkus parent-first (boot) JAR name patterns.
     *
     * These JARs must be on the system classloader (lib/boot/) because
     * QuarkusEntryPoint needs them before the Quarkus classloader is set up.
     * In Bazel builds, Quarkus augmentation may place them all in lib/main/
     * because the ApplicationModel lacks classloading parent-first flags.
     */
    private static final List<String> BOOT_JAR_PATTERNS = List.of(
        // Quarkus bootstrap
        "quarkus-bootstrap-runner",
        "quarkus-classloader-commons",
        "quarkus-development-mode-spi",
        "quarkus-fs-util",
        // JBoss logging/modules
        "jboss-logmanager",
        "jboss-logging",
        "jboss-modules",
        "jboss-threads",
        "slf4j-jboss-logmanager",
        "wildfly-common",
        // SmallRye common (required by jboss-logmanager)
        "smallrye-common",
        // CRaC checkpoint/restore
        "org-crac",
        "crac"
    );

    /**
     * Fix the boot classpath by moving parent-first JARs from lib/main/ to lib/boot/.
     *
     * In standard Maven builds, Quarkus places these JARs in lib/boot/ automatically.
     * In Bazel builds, they end up in lib/main/ because the ApplicationModel doesn't
     * carry the CLASSLOADING_PARENT_FIRST_ARTIFACT flag. We fix this post-hoc.
     */
    private static void ensureBootstrapRunner(AugmentationConfig config, Path targetDir) throws IOException {
        Path bootDir = targetDir.resolve("lib").resolve("boot");
        Path mainDir = targetDir.resolve("lib").resolve("main");
        Files.createDirectories(bootDir);

        // Copy matching JARs from lib/main/ to lib/boot/.
        // We COPY (not move) because quarkus-application.dat references JARs by
        // their lib/main/ paths. RunnerClassLoader needs them there for non-parent-first
        // classes, while the system classloader needs them in lib/boot/ for parent-first classes.
        long copiedCount = 0;
        if (Files.exists(mainDir)) {
            List<Path> bootJars;

            try (var stream = Files.list(mainDir)) {
                bootJars = stream.filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return BOOT_JAR_PATTERNS.stream().anyMatch(name::contains);
                }).toList();
            }

            for (Path jar : bootJars) {
                // Fail the build on a copy error. Logging and moving on kept the build green, and a
                // missing boot JAR then only killed the app at STARTUP, with no trace pointing here.
                // The "bootDir empty" check below can't catch it: 1 missing jar out of N slips by.
                Path target = bootDir.resolve(jar.getFileName());
                Files.copy(jar, target, StandardCopyOption.REPLACE_EXISTING);
                copiedCount++;
            }
        }
        
        if (copiedCount > 0) {
            System.out.println("  Copied " + copiedCount + " bootstrap JARs to lib/boot/.");
        }

        // Verify boot directory is not empty.
        // Check copiedCount first (fast path); fall back to directory listing only when
        // nothing was copied, in case bootDir had pre-existing files from copyDirectory.
        if (copiedCount == 0 && isDirectoryEmpty(bootDir)) {
            System.out.println("  WARNING: lib/boot/ is still empty after fixup, copying from runtime JARs...");
            for (Path jar : config.getRuntimeJars()) {
                String fileName = jar.getFileName().toString();
                if (fileName.contains("quarkus-bootstrap-runner")) {
                    String version = extractVersion(fileName);
                    String targetFileName = "io.quarkus.quarkus-bootstrap-runner-" + version + ".jar";
                    Path targetJar = bootDir.resolve(targetFileName);
                    Files.copy(jar, targetJar, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("    Copied: " + fileName + " -> " + targetFileName);
                    break;
                }
            }
        }
    }

    /**
     * Extract version from JAR filename.
     * E.g., "processed_quarkus-bootstrap-runner-3.17.4.jar" -> "3.17.4"
     */
    private static String extractVersion(String fileName) {
        // Remove processed_ prefix if present
        if (fileName.startsWith("processed_")) {
            fileName = fileName.substring("processed_".length());
        }
        // Remove .jar extension
        if (fileName.endsWith(".jar")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        // Find last dash followed by digit (version start)
        int lastDash = fileName.lastIndexOf('-');
        while (lastDash > 0) {
            if (lastDash + 1 < fileName.length() && Character.isDigit(fileName.charAt(lastDash + 1))) {
                return fileName.substring(lastDash + 1);
            }
            lastDash = fileName.lastIndexOf('-', lastDash - 1);
        }
        return "unknown";
    }

    /**
     * List the output structure for debugging.
     */
    public static void listOutput(AugmentResult result) throws IOException {
        Path outputDir = result.getJar().getPath().getParent();

        long[] totalSize = {0};
        int[] fileCount = {0};

        Files.walkFileTree(outputDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                totalSize[0] += attrs.size();
                fileCount[0]++;
                return FileVisitResult.CONTINUE;
            }
        });

        System.out.println("  Output summary: " + fileCount[0] + " files, " + formatSize(totalSize[0]) + " total size.");
    }

    /**
     * Recursively copy {@code source} to {@code target}. Also accepts a plain file as
     * source: walkFileTree then visits just that file and relativize yields an empty path,
     * so the file lands exactly at {@code target}.
     */
    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteDirectory(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (exc != null) throw exc;
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean isDirectoryEmpty(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.findAny().isEmpty();
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
