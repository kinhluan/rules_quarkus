package io.quarkus.bazel.bootstrap;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.List;
import java.nio.file.NoSuchFileException;
import java.util.Properties;
import java.util.zip.ZipException;
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
        return jarPaths.parallelStream()
                .map(ExtensionDetector::detectExtension)
                .filter(Objects::nonNull)
                .toList();
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

        } catch (ZipException | NoSuchFileException e) {
            // Not a valid JAR: an EXPECTED outcome while scanning a classpath, not an error.
            return null;
        } catch (Exception e) {
            // A real failure (permissions, truncated JAR, I/O): still return null so augmentation
            // doesn't break, but say so. Swallowing it silently drops an extension, and the failure
            // then surfaces somewhere else entirely - a build step that never ran, a bean that was
            // never registered - which is very hard to trace back here.
            System.err.println("  WARNING: cannot read extension metadata from "
                + jarPath.getFileName() + ": " + e);
            return null;
        }
    }

    private static ExtensionInfo createExtensionInfo(Path jarPath, Properties props) {
        MavenCoords coords = MavenCoords.fromJarPath(jarPath);

        String groupId   = coords != null ? coords.groupId()   : props.getProperty("groupId", "unknown");
        String artifactId = coords != null ? coords.artifactId() : props.getProperty("artifactId", "unknown");
        String version   = coords != null ? coords.version()   : props.getProperty("version", "unknown");

        return new ExtensionInfo(jarPath, groupId, artifactId, version);
    }

}
