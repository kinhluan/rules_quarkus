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
