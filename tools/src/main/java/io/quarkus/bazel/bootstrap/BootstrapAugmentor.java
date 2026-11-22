package io.quarkus.bazel.bootstrap;

import io.quarkus.bootstrap.app.AugmentAction;
import io.quarkus.bootstrap.app.AugmentResult;
import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.bootstrap.app.QuarkusBootstrap;
import io.quarkus.bootstrap.model.ApplicationModel;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Main entry point for Quarkus augmentation using QuarkusBootstrap API.
 *
 * This is the Approach 2 implementation that uses official Quarkus APIs
 * instead of custom augmentation logic.
 *
 * Flow:
 * 1. Parse arguments -> AugmentationConfig
 * 2. Stage Bazel-built JARs whose filenames collide
 * 3. Auto-register internal Bazel deps for CDI bean scanning
 * 4. Build ApplicationModel from Bazel dependencies
 * 5. Create QuarkusBootstrap with existing model
 * 6. Bootstrap and run augmentation
 * 7. Output augmented application
 *
 * Runs either as a one-shot process or as a Bazel persistent worker serving one request per
 * service; {@link WorkerLoop} picks between them and {@link #runOnce} is the shared body.
 */
public class BootstrapAugmentor {

    public static void main(String[] args) throws Exception {
        if (WorkerLoop.isWorkerStartup(args)) {
            new WorkerLoop().run();
            return;
        }
        runOnce(WorkerLoop.stripWorkerFlag(args));
    }

    /**
     * Run one augmentation end to end.
     *
     * Must not leave JVM-wide state behind. In worker mode one JVM serves many services in
     * sequence, so anything parked in System.getProperties() or in a static cache bleeds into
     * the next service: stale quarkus.index-dependency.bazelN entries would index the previous
     * service's artifacts. Hence build-time config travels through QuarkusBootstrap's
     * buildSystemProperties, never through System.setProperty, and per-request state is
     * dropped in the finally block.
     */
    static void runOnce(String[] args) throws Exception {
        System.out.println("Quarkus Bootstrap Augmentor (v2)");
        System.out.println("================================");
        System.out.println();

        // 1. Parse arguments
        AugmentationConfig config = ConfigParser.parse(args);
        printConfig(config);

        Path stageDir = null;
        try {
            // 2. Stage Bazel-built JARs with unique names to prevent filename collisions
            stageDir = stageBazelJars(config);

            // 3. Collect build-time properties, including CDI indexing of internal Bazel deps.
            //    Runs after staging so the registered artifactIds match the staged paths that
            //    the ApplicationModel will carry.
            Properties buildProps = buildTimeProperties(config);

            // 4. Build ApplicationModel
            System.out.println("Building ApplicationModel...");
            ApplicationModel appModel = ApplicationModelFactory.create(config);
            printModelInfo(appModel);

            // 5. Create QuarkusBootstrap
            System.out.println("Creating QuarkusBootstrap...");
            QuarkusBootstrap bootstrap = createBootstrap(config, appModel, buildProps);

            // 6. Run augmentation
            System.out.println("Running augmentation...");
            runAugmentation(bootstrap, config);

            System.out.println();
            System.out.println("Augmentation complete!");
            System.out.println("Output: " + config.getOutputDir());
        } finally {
            MavenCoords.clearCache();
            if (stageDir != null) {
                deleteStageDir(stageDir);
            }
        }
    }

    /**
     * Build-time properties handed to the isolated augmentation classloader.
     *
     * These used to be split: main-class went through setBuildSystemProperties while the
     * platform property and the quarkus.index-dependency.* keys went through
     * System.setProperty. System properties are a JVM-wide singleton, which is exactly what a
     * persistent worker cannot afford, so everything now goes through the same Properties
     * instance scoped to this one request.
     */
    private static Properties buildTimeProperties(AugmentationConfig config) {
        Properties props = new Properties();

        props.setProperty("quarkus.package.main-class", config.getMainClass());

        // Platform property that the Quarkus BOM normally provides.
        props.setProperty("platform.quarkus.native.builder-image", "mandrel");

        registerInternalDepsForIndexing(config, props);

        return props;
    }

    /**
     * Stage Bazel-built JARs with unique filenames to prevent collisions.
     *
     * Quarkus names output JARs in lib/main/ as "{groupId}.{originalFilename}".
     * Multiple Bazel targets can produce JARs with the same filename (e.g., "libbuild.jar"
     * from both libs/common-proto and services/orders/proto).
     * This causes the second JAR to overwrite the first in the output.
     *
     * We fix this by creating symlinks with unique names (based on the full artifactId)
     * in a staging directory, then replacing the JAR paths in the config.
     */
    private static Path stageBazelJars(AugmentationConfig config) throws IOException {
        // Detect filename collisions among Bazel-built JARs.
        // Cache coords to avoid parsing the same JAR path twice.
        int expectedSize = config.getRuntimeJars().size();
        Map<String, List<Path>> filenameBuckets = HashMap.newHashMap(expectedSize);
        Map<Path, MavenCoords> coordsCache = HashMap.newHashMap(expectedSize);
        for (Path jar : config.getRuntimeJars()) {
            MavenCoords coords = MavenCoords.fromJarPath(jar);
            if (coords != null && "bazel.internal".equals(coords.groupId())) {
                coordsCache.put(jar, coords);
                String filename = jar.getFileName().toString();
                filenameBuckets.computeIfAbsent(filename, k -> new ArrayList<>()).add(jar);
            }
        }

        // Find collisions (filenames used by more than one JAR).
        // Use a plain loop instead of stream to avoid Stream/Spliterator allocation.
        boolean hasCollisions = false;
        for (List<Path> bucket : filenameBuckets.values()) {
            if (bucket.size() > 1) { hasCollisions = true; break; }
        }
        if (!hasCollisions) {
            return null;
        }

        // Staging directory for uniquely-named symlinks.
        //
        // This lives inside the declared output directory rather than in a Files.createTempDirectory,
        // for two reasons:
        //  - Reproducibility. MavenCoords derives synthetic coordinates from the JAR path, so the
        //    staging path ends up inside quarkus-app-dependencies.txt. A random /tmp name made that
        //    file differ on every build, which changes the tree artifact and defeats caching of
        //    everything downstream (image layer, push). Under the output directory the path is
        //    derived from the Bazel package, so it is stable across builds and machines.
        //  - Cleanup. A temp directory needed a shutdown hook, which never fires between worker
        //    requests and leaks /tmp/bazel-jar-stage-* when the JVM is killed. Here the directory is
        //    deleted in runOnce's finally block, and anything left behind by a crash is discarded
        //    with the failed action's outputs.
        Path stageDir = config.getOutputDir().resolve(".jar-stage");
        deleteStageDir(stageDir);
        Files.createDirectories(stageDir);

        // Build a map of original path -> staged path for colliding JARs
        Map<Path, Path> stagedPaths = HashMap.newHashMap(filenameBuckets.size());
        int collisionCount = 0;
        for (var entry : filenameBuckets.entrySet()) {
            if (entry.getValue().size() > 1) {
                collisionCount += entry.getValue().size();
                for (Path jar : entry.getValue()) {
                    MavenCoords coords = coordsCache.get(jar);
                    // Use artifactId (which includes full Bazel path) as unique filename
                    String uniqueName = coords.artifactId() + ".jar";
                    Path staged = stageDir.resolve(uniqueName);
                    Files.createSymbolicLink(staged, jar.toAbsolutePath());
                    stagedPaths.put(jar, staged);
                }
            }
        }
        
        if (collisionCount > 0) {
            System.out.println("Resolved filename collisions for " + collisionCount + " JARs.");
        }

        // Replace paths in the config's runtime JARs list
        config.replaceRuntimeJarPaths(stagedPaths);

        return stageDir;
    }

    /**
     * Recursively delete the staging directory. Entries are symlinks, and walkFileTree
     * does not follow links by default, so the links themselves are removed - never
     * the JARs they point at. Also used to clear a stale directory left by a crashed run
     * before staging starts, so a missing directory is not an error.
     */
    private static void deleteStageDir(Path stageDir) {
        if (!Files.exists(stageDir)) {
            return;
        }
        try {
            Files.walkFileTree(stageDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // The directory now sits inside the declared output tree, so a failed cleanup does
            // not just leak a temp dir - it ships stray absolute symlinks into the Bazel tree
            // artifact and from there into the container image. Say so rather than swallow it.
            System.err.println("  WARNING: could not remove staging directory " + stageDir + ": " + e);
        }
    }

    /**
     * Register internal Bazel-built dependencies for Quarkus CDI bean scanning.
     *
     * Internal deps (groupId = "bazel.internal") are not scanned by default because
     * they lack META-INF/beans.xml. We register them via quarkus.index-dependency
     * build-time properties so Quarkus includes them in the Jandex index during augmentation.
     */
    private static void registerInternalDepsForIndexing(AugmentationConfig config, Properties buildProps) {
        int registered = 0;
        int idx = 0;

        for (Path jar : config.getRuntimeJars()) {
            MavenCoords coords = MavenCoords.fromJarPath(jar);
            if (coords != null && "bazel.internal".equals(coords.groupId())) {
                String key = "bazel" + idx++;
                buildProps.setProperty("quarkus.index-dependency." + key + ".group-id", coords.groupId());
                buildProps.setProperty("quarkus.index-dependency." + key + ".artifact-id", coords.artifactId());
                registered++;
            }
        }

        if (registered > 0) {
            System.out.println("Registered " + registered + " internal deps for CDI scanning.");
        }
    }

    private static void printConfig(AugmentationConfig config) {
        System.out.println("Configuration:");
        System.out.println("  Application JARs: " + config.getApplicationJars().size());
        System.out.println("  Runtime JARs:     " + config.getRuntimeJars().size());
        System.out.println("  Deployment JARs:  " + config.getDeploymentJars().size());
        System.out.println("  Output:           " + config.getOutputDir());
        System.out.println("  App name:         " + config.getApplicationName());
        System.out.println();
    }

    private static void printModelInfo(ApplicationModel model) {
        long extensionCount = 0, runtimeCount = 0, deploymentCount = 0;
        for (var dep : model.getDependencies()) {
            if (dep.isRuntimeExtensionArtifact()) extensionCount++;
            if (dep.isRuntimeCp()) runtimeCount++;
            if (dep.isDeploymentCp()) deploymentCount++;
        }

        System.out.println("ApplicationModel built:");
        System.out.println("  Total dependencies: " + model.getDependencies().size());
        System.out.println("  Runtime classpath:  " + runtimeCount);
        System.out.println("  Deployment classpath: " + deploymentCount);
        System.out.println("  Extensions:         " + extensionCount);
        System.out.println("  App artifact:       " + model.getAppArtifact().getGroupId() + ":" +
            model.getAppArtifact().getArtifactId());
        System.out.println();
    }

    private static QuarkusBootstrap createBootstrap(AugmentationConfig config, ApplicationModel appModel,
            Properties buildProps) throws Exception {

        // Pass build-time properties into the isolated augmentation classloader.
        // System.setProperty() won't work because augmentation runs in an isolated
        // classloader that doesn't inherit parent system properties.
        return QuarkusBootstrap.builder()
            .setBuildSystemProperties(buildProps)
            .setLocalProjectDiscovery(false)
            .setOffline(true)
            .setApplicationRoot(config.getApplicationRoot())
            .setExistingModel(appModel)
            .setTargetDirectory(config.getOutputDir())
            .setBaseName(config.getApplicationName())
            .setMode(QuarkusBootstrap.Mode.PROD)
            .setBaseClassLoader(BootstrapAugmentor.class.getClassLoader())
            .setIsolateDeployment(true)
            .build();
    }

    private static void runAugmentation(QuarkusBootstrap bootstrap, AugmentationConfig config)
            throws Exception {

        try (CuratedApplication curatedApp = bootstrap.bootstrap()) {
            System.out.println("  CuratedApplication created");

            AugmentAction augmentAction = curatedApp.createAugmentor();
            System.out.println("  AugmentAction created");

            AugmentResult result = augmentAction.createProductionApplication();
            System.out.println("  Production application created");

            OutputHandler.listOutput(result);
            OutputHandler.copyOutput(result, config);
        }
    }
}
