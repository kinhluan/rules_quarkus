package io.quarkus.bazel.bootstrap;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Detects Quarkus extensions from JAR files.
 *
 * Quarkus extensions contain META-INF/quarkus-extension.properties with:
 * - deployment-artifact: coordinates of the deployment module
 * - Other extension metadata
 */
public class ExtensionDetector {

    private static final String EXTENSION_PROPS = "META-INF/quarkus-extension.properties";

    /**
     * Detect all Quarkus extensions from a list of JAR files.
     */
    public static List<ExtensionInfo> detect(List<Path> jarPaths) {
        List<ExtensionInfo> extensions = new ArrayList<>();

        for (Path jarPath : jarPaths) {
            ExtensionInfo info = detectExtension(jarPath);
            if (info != null) {
                extensions.add(info);
            }
        }

        return extensions;
    }

    /**
     * Check if a JAR is a Quarkus extension.
     */
    public static boolean isQuarkusExtension(Path jarPath) {
        return detectExtension(jarPath) != null;
    }

    /**
     * Detect extension info from a single JAR.
     * Returns null if not a Quarkus extension.
     */
    public static ExtensionInfo detectExtension(Path jarPath) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            JarEntry entry = jarFile.getJarEntry(EXTENSION_PROPS);

            if (entry == null) {
                return null;
            }

            Properties props = new Properties();
            try (InputStream is = jarFile.getInputStream(entry)) {
                props.load(is);
            }

            return createExtensionInfo(jarPath, props);

        } catch (Exception e) {
            // Not a valid JAR or can't read properties
            return null;
        }
    }

    private static ExtensionInfo createExtensionInfo(Path jarPath, Properties props) {
        // Extract coordinates from JAR path (Bazel Maven layout)
        MavenCoords coords = parseMavenCoords(jarPath);

        String groupId = coords != null ? coords.groupId : props.getProperty("groupId", "unknown");
        String artifactId = coords != null ? coords.artifactId : props.getProperty("artifactId", "unknown");
        String version = coords != null ? coords.version : props.getProperty("version", "unknown");

        // deployment-artifact property contains full coordinates
        String deploymentArtifact = props.getProperty("deployment-artifact");

        return new ExtensionInfo(jarPath, groupId, artifactId, version, deploymentArtifact, props);
    }

    /**
     * Parse Maven coordinates from Bazel external JAR path.
     * Path pattern: .../maven2/GROUP_PATH/ARTIFACT/VERSION/ARTIFACT-VERSION.jar
     */
    private static MavenCoords parseMavenCoords(Path jarPath) {
        String path = jarPath.toString();
        String[] parts = path.split("/");

        String version = null;
        String artifact = null;
        List<String> groupParts = new ArrayList<>();

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
