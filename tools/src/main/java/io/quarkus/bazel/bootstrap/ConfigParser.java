package io.quarkus.bazel.bootstrap;

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
        // Expand @filename arguments from Bazel param files
        String[] expandedArgs = expandArgs(args);
        AugmentationConfig.Builder builder = AugmentationConfig.builder();

        for (int i = 0; i < expandedArgs.length; i++) {
            String arg = expandedArgs[i];

            switch (arg) {
                case "--output-dir":
                    builder.setOutputDir(Paths.get(expandedArgs[++i]));
                    break;

                case "--application-jars":
                    builder.addApplicationJars(parseJarList(expandedArgs[++i]));
                    break;

                case "--runtime-jars":
                    builder.addRuntimeJars(parseJarList(expandedArgs[++i]));
                    break;

                case "--deployment-jars":
                    builder.addDeploymentJars(parseJarList(expandedArgs[++i]));
                    break;

                case "--app-name":
                    builder.setApplicationName(expandedArgs[++i]);
                    break;

                case "--main-class":
                    builder.setMainClass(expandedArgs[++i]);
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

    /**
     * Expands arguments starting with '@' by reading the file content.
     */
    private static String[] expandArgs(String[] args) {
        List<String> expanded = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("@")) {
                Path paramFile = Paths.get(arg.substring(1));
                try {
                    expanded.addAll(Files.readAllLines(paramFile));
                } catch (IOException e) {
                    System.err.println("Error reading param file: " + paramFile);
                    e.printStackTrace();
                }
            } else {
                expanded.add(arg);
            }
        }
        return expanded.toArray(new String[0]);
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

    private static List<Path> parseJarList(String jarList) {
        List<Path> jars = new ArrayList<>();

        if (jarList == null || jarList.isEmpty()) {
            return jars;
        }

        // Handle both comma-separated and File.pathSeparator-separated
        String separator = jarList.contains(",") ? "," : java.io.File.pathSeparator;

        for (String jar : jarList.split(separator)) {
            String trimmed = jar.trim();
            if (!trimmed.isEmpty()) {
                jars.add(Paths.get(trimmed));
            }
        }

        return jars;
    }
}
