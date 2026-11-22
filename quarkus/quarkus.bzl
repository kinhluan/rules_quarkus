"""
Quarkus Application Macro (Approach 2)

Main user-facing macro for building Quarkus applications with Bazel.
Uses QuarkusBootstrap API for augmentation.
"""

load("@rules_java//java:defs.bzl", "java_library")
load("//quarkus:quarkus_bootstrap.bzl", "quarkus_bootstrap")
load("//quarkus:quarkus_runner.bzl", "quarkus_runner")

def _dedup_labels(labels):
    """Deduplicate a list of Bazel labels, preserving first-occurrence order.

    Prevents 'duplicate in deps' errors when a label appears explicitly in
    both `deps` and `runtime_extensions` — e.g. when a library in `deps`
    transitively exports a target that is also listed in `runtime_extensions`.
    """
    seen = {}
    result = []
    for label in labels:
        if label not in seen:
            seen[label] = True
            result.append(label)
    return result

def quarkus_application(
        name,
        srcs = [],
        resources = [],
        deps = [],
        runtime_deps = [],
        runtime_extensions = [],
        deployment_extensions = [],
        main_class = None,
        data = [],
        plugins = [],
        jvm_flags = [],
        visibility = None,
        tags = [],
        **kwargs):
    """
    Builds a Quarkus application using QuarkusBootstrap API.

    This macro creates three targets:
    1. {name}_lib - Compiles application sources
    2. {name}_augmented - Runs Quarkus augmentation
    3. {name} - Final executable application

    Args:
        name: Application name
        srcs: Java source files
        resources: Application resources (application.properties, etc.)
        deps: Compile-time dependencies (referenced by source code)
        runtime_deps: Runtime-only dependencies (not on compile classpath).
            Use for libraries needed only when the app runs (e.g. JSON
            serializers, JDBC drivers, CDI implementations) so changes to
            their ABI don't trigger recompilation of this app's sources.
        runtime_extensions: Quarkus runtime extension modules
            e.g., @maven//:io_quarkus_quarkus_arc
        deployment_extensions: Quarkus deployment modules
            e.g., @maven//:io_quarkus_quarkus_arc_deployment
        main_class: Main class (required). Set to your @QuarkusMain class or the main class from pom.xml.
            For standard Quarkus apps without custom @QuarkusMain, use "io.quarkus.runner.GeneratedMain"
        data: Runtime data files (e.g., config files needed by bazel run).
        plugins: Annotation processor plugins (e.g., Lombok).
        jvm_flags: JVM flags for running the application
        visibility: Target visibility
        tags: Build tags
        **kwargs: Additional arguments

    Example:
        quarkus_application(
            name = "my-app",
            srcs = glob(["src/main/java/**/*.java"]),
            resources = glob(["src/main/resources/**/*"]),
            deps = [
                "@maven//:jakarta_enterprise_jakarta_enterprise_cdi_api",
            ],
            runtime_extensions = [
                "@maven//:io_quarkus_quarkus_arc",
                "@maven//:io_quarkus_quarkus_resteasy_reactive",
            ],
            deployment_extensions = [
                "@maven//:io_quarkus_quarkus_arc_deployment",
                "@maven//:io_quarkus_quarkus_resteasy_reactive_deployment",
            ],
            main_class = "io.quarkus.runner.GeneratedMain",
        )
    """

    if not main_class:
        fail("main_class is required for quarkus_application. " +
             "Set it to your @QuarkusMain class or the main class from pom.xml. " +
             "For standard Quarkus apps without custom @QuarkusMain, use 'io.quarkus.runner.GeneratedMain'.")

    # ============================================================================
    # LAYER 1: COMPILATION
    # Compile application sources to bytecode
    # ============================================================================
    lib_name = name + "_lib"

    java_library(
        name = lib_name,
        srcs = srcs,
        resources = resources,
        deps = _dedup_labels(deps + runtime_extensions),
        runtime_deps = _dedup_labels(runtime_deps),
        plugins = plugins,
        javacopts = [
            "-parameters",  # Required for Quarkus parameter name retention
        ],
        tags = tags + ["manual"],
        visibility = ["//visibility:private"],
    )

    # ============================================================================
    # LAYER 2: AUGMENTATION
    # Run QuarkusBootstrap to generate CDI proxies, optimized bytecode, etc.
    # ============================================================================
    augmented_name = name + "_augmented"
    augmentation_runtime_deps = _dedup_labels(
        deps +
        runtime_deps +
        runtime_extensions,
    )

    # JacocoCoverage is on the augmentation classpath so `bazel coverage` works: instrumented
    # classes reference org.jacoco runtime classes that must resolve during augmentation.
    augmentation_deployment_deps = _dedup_labels(
        deployment_extensions +
        ["@bazel_tools//tools/jdk:JacocoCoverage"],
    )

    # Only the main app lib is the "application" artifact (scanned for CDI beans).
    # All other deps (internal Bazel targets + external Maven) go to runtime_deps.
    # Internal deps get CDI scanning via quarkus.index-dependency (auto-registered
    # by the augmentor for all bazel.internal.* JARs).
    quarkus_bootstrap(
        name = augmented_name,
        application = [":" + lib_name],
        runtime_deps = augmentation_runtime_deps,
        deployment_deps = augmentation_deployment_deps,
        application_name = name,
        main_class = main_class,
        tags = tags + ["manual"],
        visibility = ["//visibility:private"],
    )

    # ============================================================================
    # LAYER 3: RUNTIME
    # Create executable that runs the augmented application via shell wrapper
    # ============================================================================
    quarkus_runner(
        name = name,
        augmented_app = ":" + augmented_name,
        jvm_flags = jvm_flags,
        data = data,
        visibility = visibility,
        tags = tags,
    )

def quarkus_library(
        name,
        srcs = [],
        resources = [],
        deps = [],
        extensions = [],
        visibility = None,
        tags = [],
        **kwargs):
    """
    Creates a library that can be used as a dependency for Quarkus applications.

    This is a simple wrapper around java_library with Quarkus-compatible settings.

    Args:
        name: Library name
        srcs: Java source files
        resources: Resources
        deps: Dependencies
        extensions: Quarkus extensions used by this library
        visibility: Target visibility
        tags: Build tags
        **kwargs: Additional arguments
    """
    java_library(
        name = name,
        srcs = srcs,
        resources = resources,
        deps = deps + extensions,
        javacopts = ["-parameters"],
        visibility = visibility,
        tags = tags,
        **kwargs
    )
