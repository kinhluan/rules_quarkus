package io.quarkus.bazel.bootstrap;

import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.bootstrap.model.ApplicationModelBuilder;
import io.quarkus.maven.dependency.DependencyFlags;
import io.quarkus.maven.dependency.ResolvedDependencyBuilder;
import io.quarkus.paths.PathList;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates ApplicationModel from Bazel dependencies.
 *
 * This is the core of Approach 2 - building a proper ApplicationModel
 * that QuarkusBootstrap can use for augmentation.
 *
 * Key responsibilities:
 * 1. Set application artifact
 * 2. Add runtime dependencies with RUNTIME_CP flag
 * 3. Add deployment dependencies with DEPLOYMENT_CP flag
 * 4. Mark Quarkus extensions with RUNTIME_EXTENSION_ARTIFACT flag
 * 5. Link runtime extensions to deployment modules
 */
public class ApplicationModelFactory {

    /**
     * Create ApplicationModel from AugmentationConfig.
     */
    public static ApplicationModel create(AugmentationConfig config) throws Exception {
        ApplicationModelBuilder builder = new ApplicationModelBuilder();

        // 1. Set application artifact
        setApplicationArtifact(builder, config);

        // 2. Detect extensions in runtime JARs
        List<ExtensionInfo> extensions = ExtensionDetector.detect(config.getRuntimeJars());
        System.out.println("  Detected " + extensions.size() + " Quarkus extensions:");
        for (ExtensionInfo ext : extensions) {
            System.out.println("    - " + ext.getArtifactId() + " (" + ext.getJarPath().getFileName() + ")");
        }

        // 3. Build set of extension artifact IDs for quick lookup
        Set<String> extensionArtifactIds = new HashSet<>();
        for (ExtensionInfo ext : extensions) {
            extensionArtifactIds.add(ext.getArtifactId());
        }

        // 4. Add runtime dependencies (cả runtime và deployment)
        // Quarkus cần thấy TẤT CẢ JARs trong model
        System.out.println("  Adding dependencies:");
        addAllDependencies(builder, config.getRuntimeJars(), config.getDeploymentJars(), extensionArtifactIds);

        return builder.build();
    }

    private static void setApplicationArtifact(ApplicationModelBuilder builder, AugmentationConfig config) {
        ResolvedDependencyBuilder appBuilder = ResolvedDependencyBuilder.newInstance()
            .setGroupId("io.quarkus.bazel")
            .setArtifactId(config.getApplicationName())
            .setVersion("1.0.0-SNAPSHOT")
            .setType("jar");

        if (!config.getApplicationJars().isEmpty()) {
            appBuilder.setResolvedPaths(PathList.from(config.getApplicationJars()));
        }

        builder.setAppArtifact(appBuilder);
    }

    private static void addAllDependencies(ApplicationModelBuilder builder,
                                           List<Path> runtimeJars,
                                           List<Path> deploymentJars,
                                           Set<String> extensionArtifactIds) {

        // First, build a map of deployment JARs for quick lookup
        Map<String, Path> deploymentJarMap = new java.util.HashMap<>();
        for (Path jar : deploymentJars) {
            MavenCoords coords = parseMavenCoords(jar);
            if (coords != null) {
                String key = coords.groupId + ":" + coords.artifactId;
                deploymentJarMap.put(key, jar);
            }
        }

        // Track what we've already added to avoid duplicates
        Set<String> addedArtifacts = new HashSet<>();
        int runtimeCount = 0;
        int deploymentCount = 0;
        int extensionCount = 0;

        // 1. Add runtime dependencies - if they're also in deployment, add BOTH flags
        for (Path jar : runtimeJars) {
            MavenCoords coords = parseMavenCoords(jar);
            if (coords == null) {
                System.out.println("    WARNING: Cannot parse coordinates for: " + jar.getFileName());
                continue;
            }

            String key = coords.groupId + ":" + coords.artifactId;
            if (addedArtifacts.contains(key)) {
                continue;
            }
            addedArtifacts.add(key);

            // Runtime deps need RUNTIME_CP flag
            int flags = DependencyFlags.RUNTIME_CP;

            // If this JAR is also in deployment, add DEPLOYMENT_CP flag too
            if (deploymentJarMap.containsKey(key)) {
                flags |= DependencyFlags.DEPLOYMENT_CP;
            }

            // Mark Quarkus extensions
            if (extensionArtifactIds.contains(coords.artifactId)) {
                flags |= DependencyFlags.RUNTIME_EXTENSION_ARTIFACT;
                extensionCount++;
                System.out.println("    [EXT-RT] " + coords.artifactId + " (flags=" + flags + ")");
            }

            builder.addDependency(ResolvedDependencyBuilder.newInstance()
                .setGroupId(coords.groupId)
                .setArtifactId(coords.artifactId)
                .setVersion(coords.version)
                .setType("jar")
                .setResolvedPaths(PathList.of(jar))
                .setFlags(flags));

            runtimeCount++;
        }

        // 2. Add deployment-ONLY dependencies (ones not in runtime)
        for (Path jar : deploymentJars) {
            MavenCoords coords = parseMavenCoords(jar);
            if (coords == null) {
                System.out.println("    WARNING: Cannot parse coordinates for: " + jar.getFileName());
                continue;
            }

            String key = coords.groupId + ":" + coords.artifactId;
            if (addedArtifacts.contains(key)) {
                // Already added as runtime (with DEPLOYMENT_CP flag if applicable), skip
                continue;
            }
            addedArtifacts.add(key);

            // Deployment-only deps need DEPLOYMENT_CP flag
            builder.addDependency(ResolvedDependencyBuilder.newInstance()
                .setGroupId(coords.groupId)
                .setArtifactId(coords.artifactId)
                .setVersion(coords.version)
                .setType("jar")
                .setResolvedPaths(PathList.of(jar))
                .setFlags(DependencyFlags.DEPLOYMENT_CP));

            deploymentCount++;
        }

        System.out.println("    Added: runtime=" + runtimeCount + ", deployment-only=" + deploymentCount + ", extensions=" + extensionCount);

        // Debug: print sample of runtime deps with flags
        System.out.println("    Sample runtime deps (first 5):");
        int sample = 0;
        for (Path jar : runtimeJars) {
            if (sample >= 5) break;
            MavenCoords coords = parseMavenCoords(jar);
            if (coords != null) {
                System.out.println("      - " + coords.groupId + ":" + coords.artifactId + " flags=RUNTIME_CP" +
                    (extensionArtifactIds.contains(coords.artifactId) ? "|EXTENSION" : ""));
                sample++;
            }
        }
    }

    /**
     * Parse Maven coordinates from Bazel external JAR path.
     */
    private static MavenCoords parseMavenCoords(Path jarPath) {
        String path = jarPath.toString();
        String[] parts = path.split("/");

        String version = null;
        String artifact = null;
        java.util.List<String> groupParts = new java.util.ArrayList<>();

        boolean foundMaven = false;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("maven2") || parts[i].equals("repository")) {
                foundMaven = true;
                continue;
            }

            if (foundMaven) {
                if (i == parts.length - 2) {
                    version = parts[i];
                } else if (i == parts.length - 3) {
                    artifact = parts[i];
                } else if (i < parts.length - 3) {
                    groupParts.add(parts[i]);
                }
            }
        }

        if (artifact != null && version != null && !groupParts.isEmpty()) {
            String groupId = String.join(".", groupParts);
            return new MavenCoords(groupId, artifact, version);
        }

        // Fallback: try to extract from filename
        return parseFromFilename(jarPath);
    }

    private static MavenCoords parseFromFilename(Path jarPath) {
        String fileName = jarPath.getFileName().toString();

        // Remove .jar extension
        if (fileName.endsWith(".jar")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }

        // Try to find version separator (last dash before digit)
        int versionStart = -1;
        for (int i = fileName.length() - 1; i >= 0; i--) {
            if (fileName.charAt(i) == '-') {
                if (i + 1 < fileName.length() && Character.isDigit(fileName.charAt(i + 1))) {
                    versionStart = i;
                    break;
                }
            }
        }

        if (versionStart > 0) {
            String artifactId = fileName.substring(0, versionStart);
            String version = fileName.substring(versionStart + 1);
            return new MavenCoords("unknown", artifactId, version);
        }

        return null;
    }

    static class MavenCoords {
        final String groupId;
        final String artifactId;
        final String version;

        MavenCoords(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }
    }
}
