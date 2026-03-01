package io.quarkus.bazel.bootstrap;

import io.quarkus.bootstrap.app.AugmentAction;
import io.quarkus.bootstrap.app.AugmentResult;
import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.bootstrap.app.QuarkusBootstrap;
import io.quarkus.bootstrap.model.ApplicationModel;

import java.nio.file.Path;

/**
 * Main entry point for Quarkus augmentation using QuarkusBootstrap API.
 *
 * This is the Approach 2 implementation that uses official Quarkus APIs
 * instead of custom augmentation logic.
 *
 * Flow:
 * 1. Parse arguments -> AugmentationConfig
 * 2. Build ApplicationModel from Bazel dependencies
 * 3. Create QuarkusBootstrap with existing model
 * 4. Bootstrap and run augmentation
 * 5. Output augmented application
 */
public class BootstrapAugmentor {

    public static void main(String[] args) throws Exception {
        System.out.println("Quarkus Bootstrap Augmentor (v2)");
        System.out.println("================================");
        System.out.println();

        // 1. Parse arguments
        AugmentationConfig config = ConfigParser.parse(args);
        printConfig(config);

        // 2. Build ApplicationModel
        System.out.println("Building ApplicationModel...");
        ApplicationModel appModel = ApplicationModelFactory.create(config);
        printModelInfo(appModel);

        // 3. Create QuarkusBootstrap
        System.out.println("Creating QuarkusBootstrap...");
        QuarkusBootstrap bootstrap = createBootstrap(config, appModel);

        // 4. Run augmentation
        System.out.println("Running augmentation...");
        runAugmentation(bootstrap, config);

        System.out.println();
        System.out.println("Augmentation complete!");
        System.out.println("Output: " + config.getOutputDir());
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
        long extensionCount = model.getDependencies().stream()
            .filter(d -> d.isRuntimeExtensionArtifact())
            .count();

        long runtimeCount = model.getDependencies().stream()
            .filter(d -> d.isRuntimeCp())
            .count();

        long deploymentCount = model.getDependencies().stream()
            .filter(d -> d.isDeploymentCp())
            .count();

        System.out.println("ApplicationModel built:");
        System.out.println("  Total dependencies: " + model.getDependencies().size());
        System.out.println("  Runtime classpath:  " + runtimeCount);
        System.out.println("  Deployment classpath: " + deploymentCount);
        System.out.println("  Extensions:         " + extensionCount);

        // Debug: print sample dependencies with their flags
        System.out.println("  Sample dependencies (first 10):");
        model.getDependencies().stream()
            .limit(10)
            .forEach(d -> {
                System.out.println("    - " + d.getGroupId() + ":" + d.getArtifactId() +
                    " runtime=" + d.isRuntimeCp() +
                    " deployment=" + d.isDeploymentCp() +
                    " extension=" + d.isRuntimeExtensionArtifact() +
                    " flags=" + d.getFlags());
            });

        // Also check app artifact
        System.out.println("  App artifact: " + model.getAppArtifact().getGroupId() + ":" +
            model.getAppArtifact().getArtifactId());

        System.out.println();
    }

    private static QuarkusBootstrap createBootstrap(AugmentationConfig config, ApplicationModel appModel)
            throws Exception {

        return QuarkusBootstrap.builder()
            .setApplicationRoot(config.getApplicationRoot())
            .setExistingModel(appModel)
            .setTargetDirectory(config.getOutputDir())
            .setBaseName(config.getApplicationName())
            .setMode(QuarkusBootstrap.Mode.PROD)
            .setIsolateDeployment(false)  // Don't isolate deployment classes
            .setFlatClassPath(true)       // Use flat classpath to avoid classloader issues
            .build();
    }

    private static void runAugmentation(QuarkusBootstrap bootstrap, AugmentationConfig config)
            throws Exception {

        try (CuratedApplication curatedApp = bootstrap.bootstrap()) {
            System.out.println("  CuratedApplication created");

            // Debug: Check if AugmentActionImpl class is available
            try {
                ClassLoader augmentCl = curatedApp.getAugmentClassLoader();
                if (augmentCl == null) {
                    System.out.println("  Augment ClassLoader is NULL - trying getOrCreateAugmentClassLoader()");
                    // Try to create/get a new one
                    augmentCl = curatedApp.getOrCreateAugmentClassLoader();
                }
                if (augmentCl != null) {
                    System.out.println("  Augment ClassLoader: " + augmentCl.getClass().getName());
                    Class<?> augmentClass = augmentCl.loadClass("io.quarkus.runner.bootstrap.AugmentActionImpl");
                    System.out.println("  AugmentActionImpl class found: " + augmentClass);
                    // List constructors
                    for (java.lang.reflect.Constructor<?> ctor : augmentClass.getConstructors()) {
                        System.out.println("    Constructor: " + ctor);
                    }
                } else {
                    System.out.println("  Augment ClassLoader is still NULL!");
                    // List all available classes in current classloader
                    System.out.println("  Trying system classloader for AugmentActionImpl...");
                    try {
                        Class<?> augmentClass = Class.forName("io.quarkus.runner.bootstrap.AugmentActionImpl");
                        System.out.println("  AugmentActionImpl found in system classloader: " + augmentClass);
                        for (java.lang.reflect.Constructor<?> ctor : augmentClass.getConstructors()) {
                            System.out.println("    Constructor: " + ctor);
                        }
                    } catch (ClassNotFoundException cnfe) {
                        System.out.println("  AugmentActionImpl NOT found in system classloader");
                    }
                }
            } catch (Exception e) {
                System.out.println("  ERROR loading AugmentActionImpl: " + e.getMessage());
                e.printStackTrace();
            }

            // Create augmentor - manually because of classloader issues
            // The issue: CuratedApplication.createAugmentor() uses this.getClass().getClassLoader()
            // but the AugmentActionImpl constructor expects CuratedApplication from augment classloader
            System.out.println("  Creating AugmentAction manually to avoid classloader conflicts...");

            ClassLoader augmentCl2 = curatedApp.getOrCreateAugmentClassLoader();
            Class<?> augmentClass = augmentCl2.loadClass("io.quarkus.runner.bootstrap.AugmentActionImpl");

            // Find the constructor that takes CuratedApplication
            java.lang.reflect.Constructor<?> ctor = null;
            for (java.lang.reflect.Constructor<?> c : augmentClass.getConstructors()) {
                Class<?>[] paramTypes = c.getParameterTypes();
                if (paramTypes.length == 1 && paramTypes[0].getName().equals("io.quarkus.bootstrap.app.CuratedApplication")) {
                    ctor = c;
                    break;
                }
            }

            if (ctor == null) {
                throw new RuntimeException("Cannot find AugmentActionImpl constructor");
            }

            System.out.println("  Found constructor: " + ctor);

            // Set TCCL to augment classloader so ASM can find all classes for frame computation
            ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(augmentCl2);
                System.out.println("  Set TCCL to augment classloader");

                AugmentAction augmentAction = (AugmentAction) ctor.newInstance(curatedApp);
                System.out.println("  AugmentAction created (build steps loaded)");

                // Run augmentation - generates bytecode, CDI proxies, etc.
                AugmentResult result = augmentAction.createProductionApplication();
                System.out.println("  Production application created");

                // Show output info
                Path generatedJar = result.getJar().getPath();
                System.out.println("  Generated JAR: " + generatedJar);

                // List output structure
                OutputHandler.listOutput(result);

                // Copy to final output location
                OutputHandler.copyOutput(result, config);
            } finally {
                Thread.currentThread().setContextClassLoader(originalTccl);
            }
        }
    }
}
