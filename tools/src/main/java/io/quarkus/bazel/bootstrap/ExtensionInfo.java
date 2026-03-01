package io.quarkus.bazel.bootstrap;

import java.nio.file.Path;
import java.util.Properties;

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
    private final String deploymentArtifact;
    private final Properties properties;

    public ExtensionInfo(Path jarPath, String groupId, String artifactId, String version,
                         String deploymentArtifact, Properties properties) {
        this.jarPath = jarPath;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.deploymentArtifact = deploymentArtifact;
        this.properties = properties;
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

    /**
     * Get the deployment artifact coordinates (e.g., "io.quarkus:quarkus-arc-deployment").
     */
    public String getDeploymentArtifact() {
        return deploymentArtifact;
    }

    public Properties getProperties() {
        return properties;
    }

    /**
     * Get the expected deployment artifact ID based on convention.
     * e.g., "quarkus-arc" -> "quarkus-arc-deployment"
     */
    public String getExpectedDeploymentArtifactId() {
        if (deploymentArtifact != null && !deploymentArtifact.isEmpty()) {
            // Parse from full coordinates: groupId:artifactId:version
            String[] parts = deploymentArtifact.split(":");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        // Convention: add "-deployment" suffix
        return artifactId + "-deployment";
    }

    @Override
    public String toString() {
        return String.format("ExtensionInfo{%s:%s:%s, deployment=%s}",
            groupId, artifactId, version, deploymentArtifact);
    }
}
