"""
Quarkus Application Macro (Approach 2)

Main user-facing macro for building Quarkus applications with Bazel.
Uses QuarkusBootstrap API for augmentation.
"""

load("@rules_java//java:defs.bzl", "java_binary", "java_library")
load("//quarkus:quarkus_bootstrap.bzl", "quarkus_bootstrap")

def quarkus_application(
        name,
        srcs = [],
        resources = [],
        deps = [],
        runtime_extensions = [],
        deployment_extensions = [],
        main_class = "io.quarkus.runner.GeneratedMain",
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
        deps: Regular dependencies (non-Quarkus libraries)
        runtime_extensions: Quarkus runtime extension modules
            e.g., @maven//:io_quarkus_quarkus_arc
        deployment_extensions: Quarkus deployment modules
            e.g., @maven//:io_quarkus_quarkus_arc_deployment
        main_class: Main class (default: io.quarkus.runner.GeneratedMain)
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
        )
    """

    # ============================================================================
    # LAYER 1: COMPILATION
    # Compile application sources to bytecode
    # ============================================================================
    lib_name = name + "_lib"

    java_library(
        name = lib_name,
        srcs = srcs,
        resources = resources,
        deps = deps + runtime_extensions,
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

    quarkus_bootstrap(
        name = augmented_name,
        application = [":" + lib_name],
        runtime_deps = runtime_extensions,
        deployment_deps = deployment_extensions,
        application_name = name,
        main_class = main_class,
        tags = tags + ["manual"],
        visibility = ["//visibility:private"],
    )

    # ============================================================================
    # LAYER 3: RUNTIME
    # Create executable that runs the augmented application
    # ============================================================================

    # For now, create a simple runner that uses the augmented output
    # TODO: Create proper java_binary that uses quarkus-run.jar

    # Generate runner script
    runner_script = name + "_runner.sh"
    native.genrule(
        name = name + "_runner_script",
        outs = [runner_script],
        cmd = """
cat > $@ << 'RUNNER_EOF'
#!/bin/bash
set -e
SCRIPT_DIR="$$(cd "$$(dirname "$$0")" && pwd)"
QUARKUS_APP="$$SCRIPT_DIR/{augmented}-quarkus-app"
exec java {jvm_flags} \
    -cp "$$QUARKUS_APP/lib/boot/*:$$QUARKUS_APP/lib/main/*:$$QUARKUS_APP/quarkus-run.jar" \
    io.quarkus.bootstrap.runner.QuarkusEntryPoint "$$@"
RUNNER_EOF
chmod +x $@
""".format(
            augmented = augmented_name,
            jvm_flags = " ".join(jvm_flags),
        ),
        tags = tags + ["manual"],
        visibility = ["//visibility:private"],
    )

    # Executable target using native.filegroup + wrapper
    native.genrule(
        name = name,
        srcs = [
            ":" + name + "_runner_script",
            ":" + augmented_name,
        ],
        outs = [name + ".sh"],
        cmd = "cp $(location :" + name + "_runner_script) $@",
        executable = True,
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
