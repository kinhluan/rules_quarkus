package io.quarkus.bazel.bootstrap;

import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.bootstrap.model.ApplicationModelBuilder;
import io.quarkus.maven.dependency.DependencyFlags;
import io.quarkus.maven.dependency.ResolvedDependencyBuilder;
import io.quarkus.paths.PathList;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

        // 2. Detect extensions in runtime JARs and build lookup set in one pass
        List<ExtensionInfo> extensions = ExtensionDetector.detect(config.getRuntimeJars());
        Set<String> extensionArtifactIds = HashSet.newHashSet(extensions.size());
        System.out.println("  Detected " + extensions.size() + " Quarkus extensions.");
        for (ExtensionInfo ext : extensions) {
            extensionArtifactIds.add(ext.getArtifactId());
        }

        // 4. Add runtime dependencies (flagged for both runtime and deployment)
        // Quarkus needs to see ALL JARs in the model
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
            List<Path> absPaths = config.getApplicationJars().stream()
                .map(Path::toAbsolutePath)
                .toList();
            appBuilder.setResolvedPaths(PathList.from(absPaths));
        }

        builder.setAppArtifact(appBuilder);
    }

    /**
     * JARs already on the tool's system classloader must be excluded from the ApplicationModel.
     * QuarkusClassLoader uses child-first delegation, so having these on both the system CL
     * and augment CL causes class identity conflicts.
     *
     * IMPORTANT: Only exclude "infrastructure" JARs that are purely for bootstrapping.
     * Do NOT exclude quarkus-core or other JARs that carry META-INF/services resources
     * needed by the augmentation pipeline (e.g., Converter SPI registrations).
     */
    private static final List<String> SYSTEM_CL_ARTIFACTS = List.of(
            "quarkus-bootstrap-core", "quarkus-bootstrap-app-model");

    private static boolean isSystemCLJar(MavenCoords coords) {
        String aid = coords.artifactId();
        if (aid == null) {
            return false;
        }
        for (String target : SYSTEM_CL_ARTIFACTS) {
            // Match when the artifactId IS the target, or the target is a whole trailing
            // segment. Bounded matching (start / '.' / '-') avoids false positives such as
            // "quarkus-bootstrap-core-deployment", while still catching dotted synthetic ids
            // from fromBazelPath (e.g. "...maven.io.quarkus.quarkus-bootstrap-core") and the
            // "processed_" prefix rules_jvm_external adds to pinned jars.
            if (aid.equals(target)
                    || aid.equals("processed_" + target)
                    || aid.endsWith("." + target)
                    || aid.endsWith("-" + target)) {
                return true;
            }
        }
        return false;
    }

    private static void addAllDependencies(ApplicationModelBuilder builder,
                                           List<Path> runtimeJars,
                                           List<Path> deploymentJars,
                                           Set<String> extensionArtifactIds) {

        // Track what we've already added to avoid duplicates.
        // Pre-size to avoid rehashing: runtime + deployment covers worst case.
        Set<String> addedArtifacts = HashSet.newHashSet(runtimeJars.size() + deploymentJars.size());
        int runtimeCount = 0;
        int deploymentCount = 0;
        int extensionCount = 0;

        // 1. Add runtime dependencies - if they're also in deployment, add BOTH flags
        for (Path jar : runtimeJars) {
            MavenCoords coords = MavenCoords.fromJarPath(jar);
            if (coords == null) {
                System.out.println("    WARNING: Cannot parse coordinates for: " + jar.getFileName());
                continue;
            }

            // Skip bootstrap JARs - they must only be on the system classloader
            if (isSystemCLJar(coords)) {
                continue;
            }

            String key = coords.groupId() + ":" + coords.artifactId();
            if (addedArtifacts.contains(key)) {
                continue;
            }
            addedArtifacts.add(key);

            // Runtime deps need both RUNTIME_CP and DEPLOYMENT_CP flags.
            // DEPLOYMENT_CP is required because ApplicationModel.getDependencies()
            // only returns deps with DEPLOYMENT_CP, and the augmentation classloader
            // is built from that list. Without DEPLOYMENT_CP, runtime deps are
            // invisible during augmentation (causing "type not found in index" errors).
            int flags = DependencyFlags.RUNTIME_CP | DependencyFlags.DEPLOYMENT_CP;

            // Mark Quarkus extensions
            if (extensionArtifactIds.contains(coords.artifactId())) {
                flags |= DependencyFlags.RUNTIME_EXTENSION_ARTIFACT;
                extensionCount++;
            }

            builder.addDependency(ResolvedDependencyBuilder.newInstance()
                .setGroupId(coords.groupId())
                .setArtifactId(coords.artifactId())
                .setVersion(coords.version())
                .setType("jar")
                .setResolvedPaths(PathList.of(jar.toAbsolutePath()))
                .setFlags(flags));

            runtimeCount++;
        }

        // 2. Add deployment-ONLY dependencies (ones not in runtime)
        for (Path jar : deploymentJars) {
            MavenCoords coords = MavenCoords.fromJarPath(jar);
            if (coords == null) {
                System.out.println("    WARNING: Cannot parse coordinates for: " + jar.getFileName());
                continue;
            }

            // Skip bootstrap JARs - they must only be on the system classloader
            if (isSystemCLJar(coords)) {
                continue;
            }

            String key = coords.groupId() + ":" + coords.artifactId();
            if (addedArtifacts.contains(key)) {
                // Already added as runtime (with DEPLOYMENT_CP flag if applicable), skip
                continue;
            }
            addedArtifacts.add(key);

            // Deployment-only deps need DEPLOYMENT_CP flag
            builder.addDependency(ResolvedDependencyBuilder.newInstance()
                .setGroupId(coords.groupId())
                .setArtifactId(coords.artifactId())
                .setVersion(coords.version())
                .setType("jar")
                .setResolvedPaths(PathList.of(jar.toAbsolutePath()))
                .setFlags(DependencyFlags.DEPLOYMENT_CP));

            deploymentCount++;
        }

        System.out.println("    Added: runtime=" + runtimeCount + ", deployment-only=" + deploymentCount + ", extensions=" + extensionCount);
    }

}
