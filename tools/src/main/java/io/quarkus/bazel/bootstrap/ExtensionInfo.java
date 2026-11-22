package io.quarkus.bazel.bootstrap;

import java.nio.file.Path;

/**
 * Information about a detected Quarkus extension.
 *
 * Extracted from META-INF/quarkus-extension.properties in the JAR.
 */
public class ExtensionInfo {

    private final Path jarPath;
    private final String groupId;
    private final String artifactId;
    private final String version;

    public ExtensionInfo(Path jarPath, String groupId, String artifactId, String version) {
        this.jarPath = jarPath;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public Path getJarPath() {
        return jarPath;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return String.format("ExtensionInfo{%s:%s:%s}", groupId, artifactId, version);
    }
}
