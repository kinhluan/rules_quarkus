package io.quarkus.bazel.bootstrap;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maven coordinates parsed from Bazel external JAR paths.
 * Path pattern: .../maven2/GROUP_PATH/ARTIFACT/VERSION/ARTIFACT-VERSION.jar
 */
public record MavenCoords(String groupId, String artifactId, String version) {

    /**
     * Memoize parse results. {@link #fromJarPath(Path)} is a pure function of the path
     * string, and the same JAR path is parsed up to ~4 times across the augmentation
     * pipeline (collision staging, CDI registration, extension detection, model build).
     * ConcurrentHashMap is required because {@link ExtensionDetector} parses in parallel.
     */
    private static final ConcurrentHashMap<Path, MavenCoords> CACHE = new ConcurrentHashMap<>();

    /**
     * Parse Maven coordinates from a Bazel external JAR path.
     * Falls back to filename-based parsing if the Maven directory layout is not detected.
     * For Bazel-built JARs, creates synthetic coordinates from the path to ensure uniqueness.
     *
     * <p>Results are memoized: never returns null (Bazel synthetic coords are the last resort).
     */
    public static MavenCoords fromJarPath(Path jarPath) {
        return CACHE.computeIfAbsent(jarPath, MavenCoords::parse);
    }

    /**
     * Drop memoized results. Called at the end of every augmentation so a persistent worker
     * does not accumulate one entry per JAR of every service it has ever augmented; a build of
     * the whole repo would otherwise retain tens of thousands of paths for the JVM's lifetime.
     */
    static void clearCache() {
        CACHE.clear();
    }

    private static MavenCoords parse(Path jarPath) {
        String path = jarPath.toString();

        // Scan for maven2/repository marker without splitting
        int markerIdx = path.indexOf("/maven2/");
        int skip = 8;
        if (markerIdx == -1) {
            markerIdx = path.indexOf("/repository/");
            skip = 12;
        }

        // Bzlmod / rules_jvm_external pinned markers
        // e.g. .../bin/external/rules_jvm_external++maven+maven/org/apache/kafka/kafka-clients/3.7.1/...
        if (markerIdx == -1) {
            int extIdx = path.indexOf("/bin/external/");
            if (extIdx != -1) {
                // Find the end of the external repository name
                int repoEndSlash = path.indexOf('/', extIdx + 14);
                if (repoEndSlash != -1) {
                    markerIdx = repoEndSlash;
                    skip = 1;
                }
            }
        }

        if (markerIdx != -1) {
            int start = markerIdx + skip;
            int lastSlash = path.lastIndexOf('/');
            int versionSlash = path.lastIndexOf('/', lastSlash - 1);
            int artifactSlash = path.lastIndexOf('/', versionSlash - 1);

            if (artifactSlash >= start) {
                String artifact = path.substring(artifactSlash + 1, versionSlash);
                String version = path.substring(versionSlash + 1, lastSlash);
                String fileName = path.substring(lastSlash + 1);
                
                // Verify it's actually a Maven layout by checking if the filename contains the version
                if (path.indexOf("/maven2/") != -1 || path.indexOf("/repository/") != -1 || fileName.contains(version)) {
                    String groupPath = path.substring(start, artifactSlash);
                    return new MavenCoords(groupPath.replace('/', '.'), artifact, version);
                }
            }
        }

        // Fallback: try to extract from filename (for Maven JARs with version in name)
        MavenCoords fromFilename = fromFilename(jarPath);
        if (fromFilename != null) {
            return fromFilename;
        }

        // Bazel-built JARs: create unique synthetic coordinates from the path.
        return fromBazelPath(path);
    }

    /**
     * Parse coordinates from JAR filename only (no directory structure).
     * E.g., "quarkus-arc-3.20.1.jar" -> ("unknown", "quarkus-arc", "3.20.1")
     * Returns null for JARs without a version in the filename (handled by fromBazelPath).
     */
    private static MavenCoords fromFilename(Path jarPath) {
        String fileName = jarPath.getFileName().toString();

        if (fileName.endsWith(".jar")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }

        // Find last dash followed by digit (version start)
        for (int i = fileName.length() - 1; i >= 0; i--) {
            if (fileName.charAt(i) == '-' && i + 1 < fileName.length()
                    && Character.isDigit(fileName.charAt(i + 1))) {
                String artifactId = fileName.substring(0, i);
                if (artifactId.startsWith("processed_")) {
                    artifactId = artifactId.substring(10);
                }
                String version = fileName.substring(i + 1);
                return new MavenCoords("unknown", artifactId, version);
            }
        }

        return null;
    }

    /**
     * Create unique synthetic coordinates from a Bazel-built JAR's full path.
     * Bazel output paths follow: .../bin/PACKAGE_PATH/FILENAME.jar
     * E.g., "bazel-out/darwin_arm64-fastbuild/bin/services/orders/libs/lib_orders-proto/libbuild.jar"
     * → ("bazel.internal", "services.orders.libs.lib-orders-proto.build", "0.0.0")
     *
     * Accepts already-split parts to avoid re-splitting the path string.
     */
    private static MavenCoords fromBazelPath(String path) {
        int binIdx = path.indexOf("/bin/");
        int startIdx = (binIdx >= 0) ? binIdx + 5 : 0;
        
        if (startIdx == 0) {
            int lastSlash = path.lastIndexOf('/');
            int secondSlash = path.lastIndexOf('/', lastSlash - 1);
            int thirdSlash = path.lastIndexOf('/', secondSlash - 1);
            if (thirdSlash >= 0) {
                startIdx = thirdSlash + 1;
            }
        }

        // Build artifactId from path segments after "bin/".
        StringBuilder artifactId = new StringBuilder();
        int current = startIdx;
        while (current < path.length()) {
            int nextSlash = path.indexOf('/', current);
            boolean isLast = (nextSlash == -1);
            int end = isLast ? path.length() : nextSlash;
            
            String seg = path.substring(current, end);
            if (isLast) {
                if (seg.endsWith(".jar")) {
                    seg = seg.substring(0, seg.length() - 4);
                }
                // Strip Bazel lib prefix from filename
                if (seg.startsWith("lib")) {
                    seg = seg.substring(3);
                }
                if (seg.startsWith("processed_")) {
                    seg = seg.substring(10);
                }
            }
            
            if (!seg.isEmpty()) {
                if (artifactId.length() > 0) artifactId.append('.');
                artifactId.append(seg.replace('_', '-'));
            }
            
            if (isLast) {
                break;
            }
            current = nextSlash + 1;
        }

        if (artifactId.length() > 0) {
            return new MavenCoords("bazel.internal", artifactId.toString(), "0.0.0");
        }

        // Last resort: use full path hash
        return new MavenCoords("bazel.internal", "jar-" + Math.abs(path.hashCode()), "0.0.0");
    }
}
