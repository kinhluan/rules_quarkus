package io.quarkus.bazel.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses command-line arguments into AugmentationConfig.
 *
 * Expected arguments:
 *   --output-dir <path>
 *   --application-jars <jar1>,<jar2>,...
 *   --runtime-jars <jar1>,<jar2>,...
 *   --deployment-jars <jar1>,<jar2>,...
 *   --app-name <name>
 *   --main-class <class>
 */
public class ConfigParser {

    public static AugmentationConfig parse(String[] args) {
        // Expand Bazel param file (@file) if present
        args = expandParamFile(args);

        AugmentationConfig.Builder builder = AugmentationConfig.builder();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "--output-dir":
                    builder.setOutputDir(Paths.get(args[++i]));
                    break;

                case "--application-jars":
                    builder.addApplicationJars(parseJarList(args[++i]));
                    break;

                case "--runtime-jars":
                    builder.addRuntimeJars(parseJarList(args[++i]));
                    break;

                case "--deployment-jars":
                    builder.addDeploymentJars(parseJarList(args[++i]));
                    break;

                case "--app-name":
                    builder.setApplicationName(args[++i]);
                    break;

                case "--main-class":
                    builder.setMainClass(args[++i]);
                    break;

                default:
                    // Handle --key=value format
                    if (arg.startsWith("--") && arg.contains("=")) {
                        String[] parts = arg.substring(2).split("=", 2);
                        handleKeyValue(builder, parts[0], parts[1]);
                    } else {
                        System.err.println("Unknown argument: " + arg);
                    }
            }
        }

        return builder.build();
    }

    private static void handleKeyValue(AugmentationConfig.Builder builder, String key, String value) {
        switch (key) {
            case "output-dir":
                builder.setOutputDir(Paths.get(value));
                break;
            case "application-jars":
                builder.addApplicationJars(parseJarList(value));
                break;
            case "runtime-jars":
                builder.addRuntimeJars(parseJarList(value));
                break;
            case "deployment-jars":
                builder.addDeploymentJars(parseJarList(value));
                break;
            case "app-name":
                builder.setApplicationName(value);
                break;
            case "main-class":
                builder.setMainClass(value);
                break;
        }
    }

    /**
     * Expand Bazel param file. When args start with @path, read args from that file.
     */
    private static String[] expandParamFile(String[] args) {
        if (args.length == 1 && args[0].startsWith("@")) {
            Path paramFile = Paths.get(args[0].substring(1));
            try {
                List<String> lines = Files.readAllLines(paramFile);
                lines.removeIf(String::isEmpty);
                return lines.toArray(String[]::new);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read param file: " + paramFile, e);
            }
        }
        return args;
    }

    private static List<Path> parseJarList(String jarList) {
        if (jarList == null || jarList.isEmpty()) {
            return new ArrayList<>();
        }

        // Handle both comma-separated and File.pathSeparator-separated
        char separator = jarList.indexOf(',') >= 0 ? ',' : java.io.File.pathSeparatorChar;

        int len = jarList.length();
        int count = 1;
        for (int i = 0; i < len; i++) {
            if (jarList.charAt(i) == separator) {
                count++;
            }
        }

        List<Path> jars = new ArrayList<>(count);
        int current = 0;

        while (current < len) {
            int next = jarList.indexOf(separator, current);
            int end = next == -1 ? len : next;

            // Extract and trim inline without allocating intermediate strings
            int trimStart = current;
            while (trimStart < end && Character.isWhitespace(jarList.charAt(trimStart))) {
                trimStart++;
            }
            int trimEnd = end - 1;
            while (trimEnd >= trimStart && Character.isWhitespace(jarList.charAt(trimEnd))) {
                trimEnd--;
            }

            if (trimStart <= trimEnd) {
                jars.add(Paths.get(jarList.substring(trimStart, trimEnd + 1)));
            }

            if (next == -1) {
                break;
            }
            current = next + 1;
        }

        return jars;
    }
}
