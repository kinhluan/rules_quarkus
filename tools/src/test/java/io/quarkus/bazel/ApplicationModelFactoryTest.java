package io.quarkus.bazel;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.bazel.bootstrap.ApplicationModelFactory;
import io.quarkus.bazel.bootstrap.AugmentationConfig;
import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.maven.dependency.DependencyFlags;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Unit tests for ApplicationModelFactory.
 *
 * Tests verify:
 * 1. ApplicationModel creation from AugmentationConfig
 * 2. Runtime vs Deployment dependency classification
 * 3. Extension detection and flagging
 * 4. JAR filename collision handling
 */
@DisplayName("ApplicationModelFactory Tests")
public class ApplicationModelFactoryTest {

    @TempDir
    Path tempDir;

    private Path runtimeJar;
    private Path deploymentJar;
    private Path extensionJar;
    private Path applicationJar;

    @BeforeEach
    void setUp() throws IOException {
        // Create temporary JAR files for testing
        runtimeJar = createMockJar("runtime-dep.jar");
        deploymentJar = createMockJar("deployment-dep.jar");
        extensionJar = createMockQuarkusExtensionJar("quarkus-arc.jar");
        applicationJar = createMockJar("application.jar");
    }

    @Test
    @DisplayName("Should create ApplicationModel from valid config")
    void testCreateApplicationModel() throws Exception {
        // Given
        AugmentationConfig config = AugmentationConfig.builder()
            .addApplicationJar(applicationJar)
            .addRuntimeJar(runtimeJar)
            .addDeploymentJar(deploymentJar)
            .setOutputDir(tempDir.resolve("output"))
            .setApplicationName("test-app")
            .setMainClass("com.example.Main")
            .build();

        // When
        ApplicationModel model = ApplicationModelFactory.create(config);

        // Then
        assertNotNull(model, "ApplicationModel should be created");
        assertNotNull(model.getAppArtifact(), "App artifact should be set");
        assertEquals("test-app", model.getAppArtifact().getArtifactId());
        assertFalse(model.getDependencies().isEmpty(), "Model should have dependencies");
    }

    @Test
    @DisplayName("Should classify runtime dependencies with correct flags")
    void testRuntimeDependencyClassification() throws Exception {
        // Given
        AugmentationConfig config = AugmentationConfig.builder()
            .addApplicationJar(applicationJar)
            .addRuntimeJar(runtimeJar)
            .setOutputDir(tempDir.resolve("output"))
            .setApplicationName("test-app")
            .setMainClass("com.example.Main")
            .build();

        // When
        ApplicationModel model = ApplicationModelFactory.create(config);

        // Then
        boolean hasRuntimeDep = model.getDependencies().stream()
            .anyMatch(d -> d.isRuntimeCp());
        assertTrue(hasRuntimeDep, "Should have runtime classpath dependencies");

        // Runtime deps should also have DEPLOYMENT_CP flag for augmentation visibility
        boolean hasDeploymentFlag = model.getDependencies().stream()
            .anyMatch(d -> d.isRuntimeCp() && d.isDeploymentCp());
        assertTrue(hasDeploymentFlag, "Runtime deps should have DEPLOYMENT_CP flag");
    }

    @Test
    @DisplayName("Should classify deployment-only dependencies")
    void testDeploymentOnlyDependencyClassification() throws Exception {
        // Given
        AugmentationConfig config = AugmentationConfig.builder()
            .addApplicationJar(applicationJar)
            .addDeploymentJar(deploymentJar)
            .setOutputDir(tempDir.resolve("output"))
            .setApplicationName("test-app")
            .setMainClass("com.example.Main")
            .build();

        // When
        ApplicationModel model = ApplicationModelFactory.create(config);

        // Then
        boolean hasDeploymentDep = model.getDependencies().stream()
            .anyMatch(d -> d.isDeploymentCp() && !d.isRuntimeCp());
        assertTrue(hasDeploymentDep, "Should have deployment-only dependencies");
    }

    @Test
    @DisplayName("Should detect and flag Quarkus extensions")
    void testExtensionDetectionAndFlagging() throws Exception {
        // Given
        AugmentationConfig config = AugmentationConfig.builder()
            .addApplicationJar(applicationJar)
            .addRuntimeJar(extensionJar)
            .setOutputDir(tempDir.resolve("output"))
            .setApplicationName("test-app")
            .setMainClass("com.example.Main")
            .build();

        // When
        ApplicationModel model = ApplicationModelFactory.create(config);

        // Then
        long extensionCount = model.getDependencies().stream()
            .filter(d -> d.isRuntimeExtensionArtifact())
            .count();
        assertTrue(extensionCount > 0, "Should detect Quarkus extensions");
    }

    @Test
    @DisplayName("Should handle multiple runtime jars")
    void testMultipleRuntimeJars() throws Exception {
        // Given - create JARs with Maven-style paths to ensure they're recognized
        Path runtimeJar2 = createMockMavenJar(
            "runtime-dep-2.jar",
            "com.example",
            "runtime-dep-2",
            "1.0.0"
        );
        Path runtimeJar3 = createMockMavenJar(
            "runtime-dep-3.jar",
            "com.example",
            "runtime-dep-3",
            "1.0.0"
        );

        AugmentationConfig config = AugmentationConfig.builder()
            .addApplicationJar(applicationJar)
            .addRuntimeJar(runtimeJar)
            .addRuntimeJar(runtimeJar2)
            .addRuntimeJar(runtimeJar3)
            .setOutputDir(tempDir.resolve("output"))
            .setApplicationName("test-app")
            .setMainClass("com.example.Main")
            .build();

        // When
        ApplicationModel model = ApplicationModelFactory.create(config);

        // Then
        assertTrue(model.getDependencies().size() >= 3,
            "Should have at least 3 runtime dependencies");
    }

    @Test
    @DisplayName("Should handle empty application jars")
    void testEmptyApplicationJars() throws Exception {
        // Given
        AugmentationConfig config = AugmentationConfig.builder()
            .addRuntimeJar(runtimeJar)
            .setOutputDir(tempDir.resolve("output"))
            .setApplicationName("test-app")
            .setMainClass("com.example.Main")
            .build();

        // When
        ApplicationModel model = ApplicationModelFactory.create(config);

        // Then
        assertNotNull(model, "Model should be created even with empty application jars");
    }

    @Test
    @DisplayName("Should exclude bootstrap JARs from model")
    void testBootstrapJarExclusion() throws Exception {
        // Given - create a bootstrap JAR with quarkus-bootstrap-core coordinates
        Path bootstrapJar = createMockMavenJar(
            "quarkus-bootstrap-core.jar",
            "io.quarkus",
            "quarkus-bootstrap-core",
            "3.20.1"
        );

        AugmentationConfig config = AugmentationConfig.builder()
            .addApplicationJar(applicationJar)
            .addRuntimeJar(bootstrapJar)
            .addRuntimeJar(runtimeJar)
            .setOutputDir(tempDir.resolve("output"))
            .setApplicationName("test-app")
            .setMainClass("com.example.Main")
            .build();

        // When
        ApplicationModel model = ApplicationModelFactory.create(config);

        // Then
        boolean hasBootstrapJar = model.getDependencies().stream()
            .anyMatch(d -> "quarkus-bootstrap-core".equals(d.getArtifactId()));
        assertFalse(hasBootstrapJar,
            "Bootstrap JARs should be excluded to avoid classloader conflicts");

        // Regular runtime JAR should still be included
        boolean hasRuntimeJar = model.getDependencies().stream()
            .anyMatch(d -> !"quarkus-bootstrap-core".equals(d.getArtifactId()));
        assertTrue(hasRuntimeJar, "Regular runtime JARs should be included");
    }

    // Helper methods

    private Path createMockJar(String filename) throws IOException {
        Path jarPath = tempDir.resolve(filename);
        try (FileOutputStream fos = new FileOutputStream(jarPath.toFile());
             JarOutputStream jos = new JarOutputStream(fos)) {
            // Add a dummy entry to make it a valid JAR
            JarEntry entry = new JarEntry("META-INF/MANIFEST.MF");
            jos.putNextEntry(entry);
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.write(jos);
            jos.closeEntry();
        }
        return jarPath;
    }

    private Path createMockQuarkusExtensionJar(String filename) throws IOException {
        Path jarPath = tempDir.resolve(filename);
        try (FileOutputStream fos = new FileOutputStream(jarPath.toFile());
             JarOutputStream jos = new JarOutputStream(fos)) {
            // Add manifest
            JarEntry manifestEntry = new JarEntry("META-INF/MANIFEST.MF");
            jos.putNextEntry(manifestEntry);
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.write(jos);
            jos.closeEntry();

            // Add Quarkus extension properties
            JarEntry propsEntry = new JarEntry("META-INF/quarkus-extension.properties");
            jos.putNextEntry(propsEntry);
            Properties props = new Properties();
            props.setProperty("groupId", "io.quarkus");
            props.setProperty("artifactId", "quarkus-arc");
            props.setProperty("version", "3.20.1");
            props.setProperty("deployment-artifact", "io.quarkus:quarkus-arc-deployment:3.20.1");
            props.store(jos, "Quarkus Extension");
            jos.closeEntry();
        }
        return jarPath;
    }

    private Path createMockMavenJar(String filename, String groupId, String artifactId, String version)
            throws IOException {
        // Create Maven-style directory structure
        Path mavenDir = tempDir.resolve("maven2")
            .resolve(groupId.replace('.', File.separatorChar))
            .resolve(artifactId)
            .resolve(version);
        Files.createDirectories(mavenDir);

        Path jarPath = mavenDir.resolve(filename);
        try (FileOutputStream fos = new FileOutputStream(jarPath.toFile());
             JarOutputStream jos = new JarOutputStream(fos)) {
            JarEntry entry = new JarEntry("META-INF/MANIFEST.MF");
            jos.putNextEntry(entry);
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.write(jos);
            jos.closeEntry();
        }
        return jarPath;
    }

    // Inner class to avoid importing java.util.Properties
    private static class Properties {
        private final java.util.Properties props = new java.util.Properties();

        public void setProperty(String key, String value) {
            props.setProperty(key, value);
        }

        public void store(java.io.OutputStream os, String comment) throws IOException {
            props.store(os, comment);
        }
    }
}
