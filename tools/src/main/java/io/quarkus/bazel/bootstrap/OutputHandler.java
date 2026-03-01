package io.quarkus.bazel.bootstrap;

import io.quarkus.bootstrap.app.AugmentResult;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

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
     * Copy augmentation output to final destination.
     */
    public static void copyOutput(AugmentResult result, AugmentationConfig config) throws IOException {
        Path sourceDir = result.getJar().getPath().getParent();
        Path targetDir = config.getOutputDir();

        System.out.println("  Copying output from: " + sourceDir);
        System.out.println("  Copying output to: " + targetDir);

        // Ensure target directory exists
        Files.createDirectories(targetDir);

        // Copy entire quarkus-app directory
        copyDirectory(sourceDir, targetDir);

        // Ensure lib/boot/ has quarkus-bootstrap-runner.jar
        ensureBootstrapRunner(config, targetDir);

        System.out.println("  Output copied successfully");
    }

    /**
     * Ensure lib/boot/ directory has the bootstrap runner JAR.
     * This is needed to run the Quarkus application.
     */
    private static void ensureBootstrapRunner(AugmentationConfig config, Path targetDir) throws IOException {
        Path bootDir = targetDir.resolve("lib").resolve("boot");
        Files.createDirectories(bootDir);

        // Check if boot directory is empty
        boolean bootEmpty = true;
        if (Files.exists(bootDir)) {
            try (var stream = Files.list(bootDir)) {
                bootEmpty = stream.findAny().isEmpty();
            }
        }

        if (bootEmpty) {
            System.out.println("  lib/boot/ is empty, copying bootstrap-runner from runtime JARs...");

            // Find quarkus-bootstrap-runner in runtime JARs
            for (Path jar : config.getRuntimeJars()) {
                String fileName = jar.getFileName().toString();
                if (fileName.contains("quarkus-bootstrap-runner")) {
                    // Extract version from filename (e.g., processed_quarkus-bootstrap-runner-3.17.4.jar)
                    String version = extractVersion(fileName);
                    // Quarkus expects: io.quarkus.quarkus-bootstrap-runner-VERSION.jar
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
     * Create a single uber-JAR from the augmented output.
     * This is useful for Bazel java_binary targets that expect a single JAR.
     */
    public static Path createUberJar(AugmentResult result, AugmentationConfig config) throws IOException {
        Path quarkusAppDir = result.getJar().getPath().getParent();
        Path uberJarPath = config.getOutputDir().resolve(config.getApplicationName() + "-runner.jar");

        // For now, just copy the quarkus-run.jar as the main artifact
        Path quarkusRunJar = quarkusAppDir.resolve("quarkus-run.jar");

        if (Files.exists(quarkusRunJar)) {
            Files.copy(quarkusRunJar, uberJarPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("  Created runner JAR: " + uberJarPath);
        } else {
            System.out.println("  WARNING: quarkus-run.jar not found");
        }

        return uberJarPath;
    }

    /**
     * List the output structure for debugging.
     */
    public static void listOutput(AugmentResult result) throws IOException {
        Path outputDir = result.getJar().getPath().getParent();

        System.out.println("  Output structure:");
        Files.walkFileTree(outputDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String relativePath = outputDir.relativize(file).toString();
                long size = attrs.size();
                System.out.println("    " + relativePath + " (" + formatSize(size) + ")");
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(outputDir)) {
                    String relativePath = outputDir.relativize(dir).toString();
                    System.out.println("    " + relativePath + "/");
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

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
