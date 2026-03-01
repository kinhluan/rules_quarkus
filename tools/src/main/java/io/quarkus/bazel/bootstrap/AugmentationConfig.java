package io.quarkus.bazel.bootstrap;

import io.quarkus.bootstrap.model.PathsCollection;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for Quarkus augmentation.
 *
 * Contains all inputs needed to run QuarkusBootstrap:
 * - Application JARs (compiled user code)
 * - Runtime JARs (Quarkus runtime extensions)
 * - Deployment JARs (Quarkus deployment modules)
 * - Output directory
 * - Application metadata
 */
public class AugmentationConfig {

    private final List<Path> applicationJars;
    private final List<Path> runtimeJars;
    private final List<Path> deploymentJars;
    private final Path outputDir;
    private final String applicationName;
    private final String mainClass;

    private AugmentationConfig(Builder builder) {
        this.applicationJars = Collections.unmodifiableList(new ArrayList<>(builder.applicationJars));
        this.runtimeJars = Collections.unmodifiableList(new ArrayList<>(builder.runtimeJars));
        this.deploymentJars = Collections.unmodifiableList(new ArrayList<>(builder.deploymentJars));
        this.outputDir = builder.outputDir;
        this.applicationName = builder.applicationName;
        this.mainClass = builder.mainClass;
    }

    public List<Path> getApplicationJars() {
        return applicationJars;
    }

    public List<Path> getRuntimeJars() {
        return runtimeJars;
    }

    public List<Path> getDeploymentJars() {
        return deploymentJars;
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getMainClass() {
        return mainClass;
    }

    /**
     * Get application root as PathsCollection for QuarkusBootstrap.
     */
    public PathsCollection getApplicationRoot() {
        return PathsCollection.from(applicationJars);
    }

    /**
     * Get all runtime classpath JARs (application + runtime deps).
     */
    public List<Path> getRuntimeClasspath() {
        List<Path> classpath = new ArrayList<>();
        classpath.addAll(applicationJars);
        classpath.addAll(runtimeJars);
        return classpath;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<Path> applicationJars = new ArrayList<>();
        private List<Path> runtimeJars = new ArrayList<>();
        private List<Path> deploymentJars = new ArrayList<>();
        private Path outputDir;
        private String applicationName = "application";
        private String mainClass = "io.quarkus.runner.GeneratedMain";

        public Builder addApplicationJar(Path jar) {
            this.applicationJars.add(jar);
            return this;
        }

        public Builder addApplicationJars(List<Path> jars) {
            this.applicationJars.addAll(jars);
            return this;
        }

        public Builder addRuntimeJar(Path jar) {
            this.runtimeJars.add(jar);
            return this;
        }

        public Builder addRuntimeJars(List<Path> jars) {
            this.runtimeJars.addAll(jars);
            return this;
        }

        public Builder addDeploymentJar(Path jar) {
            this.deploymentJars.add(jar);
            return this;
        }

        public Builder addDeploymentJars(List<Path> jars) {
            this.deploymentJars.addAll(jars);
            return this;
        }

        public Builder setOutputDir(Path outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        public Builder setApplicationName(String applicationName) {
            this.applicationName = applicationName;
            return this;
        }

        public Builder setMainClass(String mainClass) {
            this.mainClass = mainClass;
            return this;
        }

        public AugmentationConfig build() {
            if (outputDir == null) {
                throw new IllegalStateException("outputDir is required");
            }
            return new AugmentationConfig(this);
        }
    }
}
