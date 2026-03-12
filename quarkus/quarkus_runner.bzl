"""
Quarkus Runner Rule

This rule creates an executable shell script that properly runs a Quarkus application
using Bazel runfiles. It resolves the augmented app directory from runfiles and
launches Quarkus through the boot classloader.
"""

def _quarkus_runner_impl(ctx):
    """
    Implementation of quarkus_runner rule.

    Creates a shell script that:
    1. Resolves augmented app dir from Bazel runfiles
    2. Supports BUILD_WORKSPACE_DIRECTORY when bazel run
    3. Launches via boot classloader
    """

    # Create the runner script
    script = ctx.actions.declare_file(ctx.label.name)

    # Get the augmented app target (single label, not a list)
    augmented_app = ctx.attr.augmented_app

    # Get the augmented app directory file from DefaultInfo
    augmented_files = augmented_app[DefaultInfo].files.to_list()
    if not augmented_files:
        fail("augmented_app must produce at least one file")
    augmented_dir = augmented_files[0]

    # Build the script content
    script_content = r"""#!/bin/bash
set -e

# Resolve the runfiles directory
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Try multiple possible runfiles locations in order of preference
RUNFILES_DIR=""

# Case 1: Environment variable from bazel (checked first)
if [ -n "${{RUNFILES_DIR:-}}" ] && [ -d "${{RUNFILES_DIR:-}}" ]; then
    RUNFILES_DIR="${{RUNFILES_DIR:-}}"
fi

# Case 2: _RUNFILES environment variable
if [ -z "$RUNFILES_DIR" ] && [ -n "${{_RUNFILES:-}}" ] && [ -d "${{_RUNFILES:-}}" ]; then
    RUNFILES_DIR="${{_RUNFILES:-}}"
fi

# Case 3: Direct .runfiles next to script (bazel build output)
if [ -z "$RUNFILES_DIR" ] && [ -d "$SCRIPT_DIR.runfiles" ]; then
    RUNFILES_DIR="$SCRIPT_DIR.runfiles"
fi

# Resolve augmented app directory from runfiles
# Path format: workspace_name/package_path/target_name-quarkus-app
AUGMENTED_APP_SHORT_PATH="{augmented_short_path}"
AUGMENTED_APP=""

# Try to find the augmented app in multiple locations
if [ -n "$RUNFILES_DIR" ]; then
    # Case A: From runfiles (standard bazel run)
    # Try _main workspace first
    AUGMENTED_APP="$RUNFILES_DIR/_main/$AUGMENTED_APP_SHORT_PATH"
    
    # Fallback: Try with workspace name from BUILD_WORKSPACE_DIRECTORY
    if [ ! -d "$AUGMENTED_APP" ] && [ -n "$BUILD_WORKSPACE_DIRECTORY" ]; then
        WORKSPACE_NAME="$(basename $BUILD_WORKSPACE_DIRECTORY)"
        AUGMENTED_APP="$RUNFILES_DIR/$WORKSPACE_NAME/$AUGMENTED_APP_SHORT_PATH"
    fi
    
    # Fallback: Try rules_quarkus workspace name
    if [ ! -d "$AUGMENTED_APP" ]; then
        AUGMENTED_APP="$RUNFILES_DIR/rules_quarkus/$AUGMENTED_APP_SHORT_PATH"
    fi
fi

# Case B: Direct path (when running from execroot without runfiles)
# The augmented app is in the same directory as this script
if [ -z "$AUGMENTED_APP" ] || [ ! -d "$AUGMENTED_APP" ]; then
    # Try same directory first (for bazel run from execroot)
    APP_BASE_NAME="$(basename $AUGMENTED_APP_SHORT_PATH)"
    DIRECT_APP_PATH="$SCRIPT_DIR/$APP_BASE_NAME"
    if [ -d "$DIRECT_APP_PATH" ]; then
        AUGMENTED_APP="$DIRECT_APP_PATH"
    fi
fi

if [ -z "$AUGMENTED_APP" ] || [ ! -d "$AUGMENTED_APP" ]; then
    echo "ERROR: Cannot find augmented app" >&2
    echo "SCRIPT_DIR=$SCRIPT_DIR" >&2
    echo "AUGMENTED_APP_SHORT_PATH=$AUGMENTED_APP_SHORT_PATH" >&2
    echo "RUNFILES_DIR=${{RUNFILES_DIR:-<not set>}}" >&2
    echo "Searched:" >&2
    if [ -n "$RUNFILES_DIR" ]; then
        echo "  $RUNFILES_DIR/_main/$AUGMENTED_APP_SHORT_PATH" >&2
        if [ -n "$BUILD_WORKSPACE_DIRECTORY" ]; then
            WORKSPACE_NAME="$(basename $BUILD_WORKSPACE_DIRECTORY)"
            echo "  $RUNFILES_DIR/$WORKSPACE_NAME/$AUGMENTED_APP_SHORT_PATH" >&2
        fi
        echo "  $RUNFILES_DIR/rules_quarkus/$AUGMENTED_APP_SHORT_PATH" >&2
    fi
    echo "  $DIRECT_APP_PATH" >&2
    exit 1
fi

# Set Quarkus application directory
export QUARKUS_APP="$AUGMENTED_APP"

# Launch Quarkus through boot classloader
# Include both boot and main classpath entries for proper classloading
exec java {jvm_flags} \
    -Djava.util.logging.manager=org.jboss.logmanager.LogManager \
    -cp "$QUARKUS_APP/lib/boot/*:$QUARKUS_APP/lib/main/*" \
    io.quarkus.bootstrap.runner.QuarkusEntryPoint "$@"
""".format(
        augmented_short_path = augmented_dir.short_path,
        jvm_flags = " ".join(ctx.attr.jvm_flags),
    )

    ctx.actions.write(
        output = script,
        content = script_content,
        is_executable = True,
    )

    # Set up runfiles to include the augmented app directory
    runfiles = ctx.runfiles(
        files = [augmented_dir],
        transitive_files = augmented_app.default_runfiles.files if augmented_app.default_runfiles else None,
    )

    return [
        DefaultInfo(
            executable = script,
            runfiles = runfiles,
        ),
    ]

quarkus_runner = rule(
    implementation = _quarkus_runner_impl,
    attrs = {
        "augmented_app": attr.label(
            mandatory = True,
            doc = "The augmented Quarkus application target (from quarkus_bootstrap)",
        ),
        "jvm_flags": attr.string_list(
            default = [],
            doc = "JVM flags to pass when running the application",
        ),
    },
    executable = True,
    doc = """
    Creates an executable shell script to run a Quarkus application.

    This rule properly resolves the augmented app directory from Bazel runfiles,
    supporting both `bazel build` and `bazel run` workflows.

    Args:
        augmented_app: The augmented application target from quarkus_bootstrap
        jvm_flags: JVM flags for running the application

    Example:
        quarkus_runner(
            name = "my-app-runner",
            augmented_app = ":my-app_augmented",
            jvm_flags = ["-Xmx512m", "-Dquarkus.http.port=8080"],
            visibility = ["//visibility:public"],
        )

    Usage:
        bazel build :my-app-runner
        bazel run :my-app-runner
    """,
)
