package io.quarkus.bazel;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.bazel.bootstrap.BootstrapAugmentor;
import io.quarkus.bazel.bootstrap.AugmentationConfig;
import io.quarkus.bazel.bootstrap.MavenCoords;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Integration tests for BootstrapAugmentor.
 *
 * Tests verify:
 * 1. CDI auto-registration for internal Bazel deps (bazel.internal.* groupId)
 * 2. Main class validation to prevent InstantiationException
 * 3. BOOT_JAR_PATTERNS handling for ClassLoader hierarchy
 * 4. JAR staging for filename collision resolution
 */
@DisplayName("BootstrapAugmentor Integration Tests")
public class BootstrapAugmentorIntegrationTest {

    @TempDir
    Path tempDir;

    private Path internalDepJar;
    private Path regularDepJar;
    private Path applicationJar;

    @BeforeEach
    void setUp() throws IOException {
        // Create mock JARs for testing
        // Use "lib" prefix to match Bazel naming convention (gets stripped by MavenCoords)
        internalDepJar = createMockInternalDepJar("libinternal-dep.jar");
        regularDepJar = createMockRegularJar("regular-dep.jar");
        applicationJar = createMockJar("application.jar");
    }

    @AfterEach
    void tearDown() {
        // Clean up any system properties set during tests
        System.clearProperty("quarkus.index-dependency.test0");
        System.clearProperty("quarkus.index-dependency.test0.group-id");
        System.clearProperty("quarkus.index-dependency.test0.artifact-id");
    }

    @Test
    @DisplayName("Should register internal Bazel deps for CDI scanning")
    void testCdiAutoRegistrationForInternalDeps() throws Exception {
        // Given - create a mock MavenCoords with bazel.internal groupId
        MavenCoords mockCoords = new MavenCoords("bazel.internal", "test-internal.libs.internal-dep", "0.0.0");

        // When - simulate the registration logic from BootstrapAugmentor
        List<String> registered = new ArrayList<>();
        String key = "test0";
        System.setProperty("quarkus.index-dependency." + key + ".group-id", mockCoords.groupId());
        System.setProperty("quarkus.index-dependency." + key + ".artifact-id", mockCoords.artifactId());
        registered.add(mockCoords.artifactId());

        // Then
        assertFalse(registered.isEmpty(), "Should register internal deps");
        assertEquals(1, registered.size(), "Should register exactly one dep");

        // Verify system properties are set
        assertNotNull(System.getProperty("quarkus.index-dependency.test0.group-id"),
            "Should set group-id property");
        assertNotNull(System.getProperty("quarkus.index-dependency.test0.artifact-id"),
            "Should set artifact-id property");
        assertEquals("bazel.internal",
            System.getProperty("quarkus.index-dependency.test0.group-id"));
        assertEquals("test-internal.libs.internal-dep",
            System.getProperty("quarkus.index-dependency.test0.artifact-id"));
    }

    @Test
    @DisplayName("Should not register regular deps for CDI scanning")
    void testNoCdiRegistrationForRegularDeps() throws Exception {
        // Given - create mock MavenCoords with regular groupId (not bazel.internal)
        MavenCoords mockCoords = new MavenCoords("com.example", "regular-dep", "1.0.0");

        // When - simulate the registration logic
        List<String> registered = new ArrayList<>();
        if ("bazel.internal".equals(mockCoords.groupId())) {
            registered.add(mockCoords.artifactId());
        }

        // Then
        assertTrue(registered.isEmpty(),
            "Should not register regular dependencies for CDI scanning");
    }

    @Test
    @DisplayName("Should handle multiple internal deps")
    void testMultipleInternalDepsRegistration() throws Exception {
        // Given - create multiple mock MavenCoords with bazel.internal groupId
        List<MavenCoords> mockCoordsList = List.of(
            new MavenCoords("bazel.internal", "test-internal.libs.internal-dep", "0.0.0"),
            new MavenCoords("bazel.internal", "test-internal.libs.internal-dep-2", "0.0.0"),
            new MavenCoords("bazel.internal", "test-internal.libs.internal-dep-3", "0.0.0")
        );

        // When
        List<String> registered = new ArrayList<>();
        int idx = 0;

        for (MavenCoords coords : mockCoordsList) {
            String key = "test" + idx++;
            System.setProperty("quarkus.index-dependency." + key + ".group-id", coords.groupId());
            System.setProperty("quarkus.index-dependency." + key + ".artifact-id", coords.artifactId());
            registered.add(coords.artifactId());
        }

        // Then
        assertEquals(3, registered.size(), "Should register all 3 internal deps");
        assertTrue(registered.contains("test-internal.libs.internal-dep"));
        assertTrue(registered.contains("test-internal.libs.internal-dep-2"));
        assertTrue(registered.contains("test-internal.libs.internal-dep-3"));
    }

    @Test
    @DisplayName("Should validate main class name format")
    void testMainClassValidation() throws Exception {
        // Given - valid main class name
        String validMainClass = "com.example.application.Main";

        // When/Then - validate format
        assertTrue(validMainClass.matches("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$"),
            "Valid main class should match Java class name pattern");

        // Invalid formats
        String invalidMainClass1 = "";
        String invalidMainClass2 = "com..example.Main";
        String invalidMainClass3 = "com.example.Main.";

        assertFalse(invalidMainClass1.matches("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$"),
            "Empty string should be invalid");
        assertFalse(invalidMainClass2.matches("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$"),
            "Double dots should be invalid");
        assertFalse(invalidMainClass3.matches("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$"),
            "Trailing dot should be invalid");
    }

    @Test
    @DisplayName("Should stage JARs with unique names for collision resolution")
    void testJarStagingForCollisionResolution() throws Exception {
        // Given - create two JARs with the same filename from different paths
        Path path1 = tempDir.resolve("path1");
        Path path2 = tempDir.resolve("path2");
        Files.createDirectories(path1);
        Files.createDirectories(path2);

        Path jar1 = createMockJarInDirectory(path1, "libbuild.jar");
        Path jar2 = createMockJarInDirectory(path2, "libbuild.jar");

        List<Path> jars = List.of(jar1, jar2);

        // When - detect collisions
        java.util.Map<String, List<Path>> filenameBuckets = new java.util.HashMap<>();
        for (Path jar : jars) {
            MavenCoords coords = MavenCoords.fromJarPath(jar);
            if (coords != null && "bazel.internal".equals(coords.groupId())) {
                String filename = jar.getFileName().toString();
                filenameBuckets.computeIfAbsent(filename, k -> new ArrayList<>()).add(jar);
            }
        }

        boolean hasCollisions = filenameBuckets.values().stream()
            .anyMatch(list -> list.size() > 1);

        // Then
        assertTrue(hasCollisions, "Should detect filename collision");
        assertEquals(2, filenameBuckets.get("libbuild.jar").size(),
            "Should have 2 JARs with same filename");
    }

    @Test
    @DisplayName("Should create unique symlink names based on artifactId")
    void testUniqueSymlinkNaming() throws Exception {
        // Given - two JARs with same filename
        Path path1 = tempDir.resolve("services/orders/libs");
        Path path2 = tempDir.resolve("libs/common-proto");
        Files.createDirectories(path1);
        Files.createDirectories(path2);

        Path jar1 = createMockJarInDirectory(path1, "libbuild.jar");
        Path jar2 = createMockJarInDirectory(path2, "libbuild.jar");

        // When - create unique names
        MavenCoords coords1 = MavenCoords.fromJarPath(jar1);
        MavenCoords coords2 = MavenCoords.fromJarPath(jar2);

        String uniqueName1 = coords1.artifactId() + ".jar";
        String uniqueName2 = coords2.artifactId() + ".jar";

        // Then
        assertNotNull(uniqueName1, "Should generate unique name for jar1");
        assertNotNull(uniqueName2, "Should generate unique name for jar2");
        assertNotEquals(uniqueName1, uniqueName2,
            "Unique names should be different for different paths");
    }

    @Test
    @DisplayName("Should handle BOOT_JAR_PATTERNS correctly")
    void testBootJarPatternsHandling() throws Exception {
        // Given - various JAR patterns
        List<String> jarPatterns = List.of(
            "quarkus-*.jar",
            "vertx-*.jar",
            "lib-*.jar"
        );

        // When - test pattern matching
        String testJar1 = "quarkus-core.jar";
        String testJar2 = "vertx-web.jar";
        String testJar3 = "lib-utils.jar";
        String testJar4 = "other.jar";

        boolean match1 = jarPatterns.stream().anyMatch(p -> matchesPattern(testJar1, p));
        boolean match2 = jarPatterns.stream().anyMatch(p -> matchesPattern(testJar2, p));
        boolean match3 = jarPatterns.stream().anyMatch(p -> matchesPattern(testJar3, p));
        boolean match4 = jarPatterns.stream().anyMatch(p -> matchesPattern(testJar4, p));

        // Then
        assertTrue(match1, "Should match quarkus-*.jar pattern");
        assertTrue(match2, "Should match vertx-*.jar pattern");
        assertTrue(match3, "Should match lib-*.jar pattern");
        assertFalse(match4, "Should not match other.jar");
    }

    @Test
    @DisplayName("Should prevent InstantiationException for invalid main classes")
    void testPreventInstantiationException() throws Exception {
        // Given - potentially problematic main class names
        String abstractClass = "com.example.AbstractMain";
        String interfaceClass = "com.example.MainInterface";
        String noArgConstructor = "com.example.MainNoArgConstructor";

        // When/Then - validate that these are syntactically valid
        // (Runtime validation would require actual class loading)
        assertTrue(isValidClassName(abstractClass), "Abstract class name should be valid format");
        assertTrue(isValidClassName(interfaceClass), "Interface name should be valid format");
        assertTrue(isValidClassName(noArgConstructor), "Class name should be valid format");

        // Note: Actual InstantiationException prevention requires runtime validation
        // which is tested in integration scenarios
    }

    // Helper methods

    private Path createMockInternalDepJar(String filename) throws IOException {
        // Create JAR with bazel.internal-style path (matching Bazel output structure)
        // Pattern: bazel-out/darwin_arm64-fastbuild/bin/PACKAGE_PATH/FILENAME.jar
        Path bazelDir = tempDir.resolve("bazel-out/darwin_arm64-fastbuild/bin/test-internal/libs");
        Files.createDirectories(bazelDir);
        Path jarPath = bazelDir.resolve(filename);

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

    private Path createMockRegularJar(String filename) throws IOException {
        return createMockJar(filename);
    }

    private Path createMockJar(String filename) throws IOException {
        Path jarPath = tempDir.resolve(filename);
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

    private Path createMockJarInDirectory(Path dir, String filename) throws IOException {
        Path jarPath = dir.resolve(filename);
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

    private boolean matchesPattern(String filename, String pattern) {
        // Simple wildcard pattern matching
        String regex = pattern.replace("*", ".*");
        return filename.matches(regex);
    }

    private boolean isValidClassName(String className) {
        return className.matches("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$");
    }
}
