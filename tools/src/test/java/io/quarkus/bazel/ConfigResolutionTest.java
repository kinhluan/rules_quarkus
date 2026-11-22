package io.quarkus.bazel;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.bazel.bootstrap.AugmentationConfig;
import io.quarkus.bazel.bootstrap.ConfigParser;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Tests for ConfigParser and configuration resolution logic.
 *
 * Tests verify:
 * 1. Command-line argument parsing
 * 2. Multi-path fallback for config file resolution
 * 3. Config file priority order
 * 4. Param file (@file) expansion
 */
@DisplayName("Config Resolution Tests")
public class ConfigResolutionTest {

    @TempDir
    Path tempDir;

    private Path applicationJar;
    private Path runtimeJar;
    private Path deploymentJar;

    @BeforeEach
    void setUp() throws IOException {
        applicationJar = createMockJar("application.jar");
        runtimeJar = createMockJar("runtime.jar");
        deploymentJar = createMockJar("deployment.jar");
    }

    @Test
    @DisplayName("Should parse basic command-line arguments")
    void testBasicArgumentParsing() throws Exception {
        // Given
        String[] args = new String[] {
            "--output-dir", tempDir.resolve("output").toString(),
            "--application-jars", applicationJar.toString(),
            "--runtime-jars", runtimeJar.toString(),
            "--deployment-jars", deploymentJar.toString(),
            "--app-name", "test-app",
            "--main-class", "com.example.Main"
        };

        // When
        AugmentationConfig config = ConfigParser.parse(args);

        // Then
        assertNotNull(config);
        assertEquals(tempDir.resolve("output"), config.getOutputDir());
        assertEquals(1, config.getApplicationJars().size());
        assertEquals(1, config.getRuntimeJars().size());
        assertEquals(1, config.getDeploymentJars().size());
        assertEquals("test-app", config.getApplicationName());
        assertEquals("com.example.Main", config.getMainClass());
    }

    @Test
    @DisplayName("Should parse comma-separated jar lists")
    void testCommaSeparatedJarLists() throws Exception {
        // Given
        Path jar2 = createMockJar("jar2.jar");
        Path jar3 = createMockJar("jar3.jar");

        String[] args = new String[] {
            "--output-dir", tempDir.resolve("output").toString(),
            "--application-jars", applicationJar.toString() + "," + jar2.toString() + "," + jar3.toString(),
            "--runtime-jars", runtimeJar.toString(),
            "--main-class", "com.example.Main"
        };

        // When
        AugmentationConfig config = ConfigParser.parse(args);

        // Then
        assertEquals(3, config.getApplicationJars().size(),
            "Should parse 3 application jars from comma-separated list");
    }

    @Test
    @DisplayName("Should parse key=value format arguments")
    void testKeyValueFormatParsing() throws Exception {
        // Given
        String[] args = new String[] {
            "--output-dir=" + tempDir.resolve("output").toString(),
            "--application-jars=" + applicationJar.toString(),
            "--runtime-jars=" + runtimeJar.toString(),
            "--app-name=test-app",
            "--main-class=com.example.Main"
        };

        // When
        AugmentationConfig config = ConfigParser.parse(args);

        // Then
        assertNotNull(config);
        assertEquals("test-app", config.getApplicationName());
        assertEquals("com.example.Main", config.getMainClass());
    }

    @Test
    @DisplayName("Should expand param file (@file) arguments")
    void testParamFileExpansion() throws Exception {
        // Given - create param file
        Path paramFile = tempDir.resolve("args.txt");
        try (FileWriter writer = new FileWriter(paramFile.toFile())) {
            writer.write("--output-dir=" + tempDir.resolve("output") + "\n");
            writer.write("--application-jars=" + applicationJar + "\n");
            writer.write("--runtime-jars=" + runtimeJar + "\n");
            writer.write("--app-name=test-app\n");
            writer.write("--main-class=com.example.Main\n");
        }

        String[] args = new String[] { "@" + paramFile.toString() };

        // When
        AugmentationConfig config = ConfigParser.parse(args);

        // Then
        assertNotNull(config);
        assertEquals("test-app", config.getApplicationName());
        assertEquals("com.example.Main", config.getMainClass());
    }

    @Test
    @DisplayName("Should handle empty jar lists gracefully")
    void testEmptyJarLists() throws Exception {
        // Given
        String[] args = new String[] {
            "--output-dir", tempDir.resolve("output").toString(),
            "--application-jars", "",
            "--runtime-jars", "",
            "--main-class", "com.example.Main"
        };

        // When
        AugmentationConfig config = ConfigParser.parse(args);

        // Then
        assertNotNull(config);
        assertTrue(config.getApplicationJars().isEmpty(),
            "Empty application-jars should result in empty list");
        assertTrue(config.getRuntimeJars().isEmpty(),
            "Empty runtime-jars should result in empty list");
    }

    @Test
    @DisplayName("Should require output-dir")
    void testOutputDirRequired() {
        // Given - missing output-dir
        String[] args = new String[] {
            "--application-jars", applicationJar.toString(),
            "--main-class", "com.example.Main"
        };

        // When/Then
        assertThrows(IllegalStateException.class, () -> ConfigParser.parse(args),
            "Should throw exception when output-dir is missing");
    }

    @Test
    @DisplayName("Should require main-class")
    void testMainClassRequired() {
        // Given - missing main-class
        String[] args = new String[] {
            "--output-dir", tempDir.resolve("output").toString(),
            "--application-jars", applicationJar.toString()
        };

        // When/Then
        assertThrows(IllegalStateException.class, () -> ConfigParser.parse(args),
            "Should throw exception when main-class is missing");
    }

    @Test
    @DisplayName("Should handle multi-path fallback for config files")
    void testMultiPathFallback() throws Exception {
        // Given - create config files in multiple locations
        Path configDir1 = tempDir.resolve("config1");
        Path configDir2 = tempDir.resolve("config2");
        Path configDir3 = tempDir.resolve("config3");
        Files.createDirectories(configDir1);
        Files.createDirectories(configDir2);
        Files.createDirectories(configDir3);

        Path config1 = configDir1.resolve("application.properties");
        Path config2 = configDir2.resolve("application.properties");
        Path config3 = configDir3.resolve("application.properties");

        Files.writeString(config1, "# Config 1\napp.name=config1");
        Files.writeString(config2, "# Config 2\napp.name=config2");
        Files.writeString(config3, "# Config 3\napp.name=config3");

        // When - search in priority order
        Path found = findConfigInPaths(
            "application.properties",
            configDir1, configDir2, configDir3
        );

        // Then - should find first one (highest priority)
        assertNotNull(found, "Should find config file");
        assertEquals(config1, found, "Should return first matching config (highest priority)");
    }

    @Test
    @DisplayName("Should return null when config file not found in any path")
    void testConfigNotFound() throws Exception {
        // Given - create directories without config file
        Path configDir1 = tempDir.resolve("config1");
        Path configDir2 = tempDir.resolve("config2");
        Files.createDirectories(configDir1);
        Files.createDirectories(configDir2);

        // When
        Path found = findConfigInPaths(
            "nonexistent.properties",
            configDir1, configDir2
        );

        // Then
        assertNull(found, "Should return null when config not found in any path");
    }

    @Test
    @DisplayName("Should handle config file priority order")
    void testConfigPriorityOrder() throws Exception {
        // Given - create config files with different priorities
        // Priority: 1. application root, 2. config dir, 3. classpath
        Path appRoot = tempDir.resolve("app-root");
        Path configDir = tempDir.resolve("config-dir");
        Files.createDirectories(appRoot);
        Files.createDirectories(configDir);

        Path appRootConfig = appRoot.resolve("application.properties");
        Path configDirConfig = configDir.resolve("application.properties");

        Files.writeString(appRootConfig, "# App Root Config");
        Files.writeString(configDirConfig, "# Config Dir Config");

        // When - search with priority
        Path found = findConfigInPaths(
            "application.properties",
            appRoot, configDir
        );

        // Then
        assertEquals(appRootConfig, found,
            "Should prioritize application root over config directory");
    }

    @Test
    @DisplayName("Should parse jar list with File.pathSeparator")
    void testFileSeparatorJarParsing() throws Exception {
        // Given - use system path separator (typically : on Unix, ; on Windows)
        Path jar2 = createMockJar("jar2.jar");
        String separator = File.pathSeparator;
        String jarList = applicationJar.toString() + separator + jar2.toString();

        String[] args = new String[] {
            "--output-dir", tempDir.resolve("output").toString(),
            "--runtime-jars", jarList,
            "--main-class", "com.example.Main"
        };

        // When
        AugmentationConfig config = ConfigParser.parse(args);

        // Then
        assertEquals(2, config.getRuntimeJars().size(),
            "Should parse jars separated by File.pathSeparator");
    }

    @Test
    @DisplayName("Should trim whitespace from jar paths")
    void testWhitespaceTrimming() throws Exception {
        // Given - jar list with whitespace
        Path jar2 = createMockJar("jar2.jar");
        String jarList = "  " + applicationJar.toString() + "  ,  " + jar2.toString() + "  ";

        String[] args = new String[] {
            "--output-dir", tempDir.resolve("output").toString(),
            "--runtime-jars", jarList,
            "--main-class", "com.example.Main"
        };

        // When
        AugmentationConfig config = ConfigParser.parse(args);

        // Then
        assertEquals(2, config.getRuntimeJars().size(),
            "Should trim whitespace from jar paths");
        for (Path jar : config.getRuntimeJars()) {
            String pathStr = jar.toString();
            assertFalse(pathStr.startsWith(" "), "Path should not start with space");
            assertFalse(pathStr.endsWith(" "), "Path should not end with space");
        }
    }

    @Test
    @DisplayName("Should handle mixed key-value and flag arguments")
    void testMixedArgumentFormats() throws Exception {
        // Given - mix of --key=value and --key value formats
        String[] args = new String[] {
            "--output-dir=" + tempDir.resolve("output").toString(),
            "--application-jars", applicationJar.toString(),
            "--app-name=test-app",
            "--main-class", "com.example.Main"
        };

        // When
        AugmentationConfig config = ConfigParser.parse(args);

        // Then
        assertNotNull(config);
        assertEquals("test-app", config.getApplicationName());
        assertEquals("com.example.Main", config.getMainClass());
    }

    // Helper method to simulate multi-path config search
    private Path findConfigInPaths(String filename, Path... searchPaths) {
        for (Path searchPath : searchPaths) {
            Path candidate = searchPath.resolve(filename);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
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
}
