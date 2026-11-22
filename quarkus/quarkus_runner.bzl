"""
Quarkus Runner Rule

Creates an executable shell script that runs a Quarkus augmented application.
The quarkus_bootstrap rule outputs a directory (not a JAR with JavaInfo), so
java_binary can't consume it as runtime_deps; this wrapper launches it instead.
"""

def _runfiles_path(f):
    """Path of `f` relative to the runfiles root.

    Files of the main repository live under `_main/`, files of external
    repositories have a short_path of `../<repo>/...` and live under `<repo>/`.
    """
    if f.short_path.startswith("../"):
        return f.short_path[3:]
    return "_main/" + f.short_path

def _quarkus_runner_impl(ctx):
    augmented_files = ctx.attr.augmented_app[DefaultInfo].files.to_list()
    if not augmented_files:
        fail("augmented_app must produce the augmented application directory")
    augmented_dir = augmented_files[0]

    jvm_flags = " ".join(['"%s"' % f.replace('"', '\\"') for f in ctx.attr.jvm_flags])

    script = ctx.actions.declare_file(ctx.label.name)
    ctx.actions.write(
        output = script,
        content = """#!/usr/bin/env bash
set -euo pipefail

# Resolve the augmented app directory from Bazel runfiles.
RUNFILES_PATH="{runfiles_path}"
APP_DIR=""
for root in "${{RUNFILES_DIR:-}}" "$0.runfiles"; do
    if [[ -n "$root" && -d "$root/$RUNFILES_PATH/lib/boot" ]]; then
        APP_DIR="$root/$RUNFILES_PATH"
        break
    fi
done
if [[ -z "$APP_DIR" ]]; then
    # Fallback: script and augmented dir are siblings in bazel-bin.
    SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
    APP_DIR="$SCRIPT_DIR/{augmented_basename}"
fi
if [[ ! -d "$APP_DIR/lib/boot" ]]; then
    echo "ERROR: augmented Quarkus app not found (looked for $RUNFILES_PATH)" >&2
    exit 1
fi

# Remote debugging: DEBUG=1 (or true) in the environment, or --debug on the command line.
# DEBUG_SUSPEND=n starts the app without waiting for a debugger.
EXTRA_JVM_FLAGS=()
APP_ARGS=()
DEBUG_ENABLED="${{DEBUG:-}}"
for arg in "$@"; do
    if [[ "$arg" == "--debug" ]]; then
        DEBUG_ENABLED=1
    else
        APP_ARGS+=("$arg")
    fi
done
if [[ "$DEBUG_ENABLED" == "1" || "$DEBUG_ENABLED" == "true" ]]; then
    EXTRA_JVM_FLAGS+=("-agentlib:jdwp=transport=dt_socket,server=y,suspend=${{DEBUG_SUSPEND:-y}},address=*:${{DEBUG_PORT:-5005}}")
fi

# Launch through the boot classpath only. quarkus-run.jar's manifest may lack a Class-Path
# in Bazel builds, and putting lib/main on the system classpath would load application
# classes twice (once there, once in Quarkus' RunnerClassLoader).
exec java {jvm_flags} ${{EXTRA_JVM_FLAGS[@]+"${{EXTRA_JVM_FLAGS[@]}}"}} \\
    -cp "$APP_DIR/lib/boot/*" \\
    io.quarkus.bootstrap.runner.QuarkusEntryPoint ${{APP_ARGS[@]+"${{APP_ARGS[@]}}"}}
""".format(
            runfiles_path = _runfiles_path(augmented_dir),
            augmented_basename = augmented_dir.basename,
            jvm_flags = jvm_flags,
        ),
        is_executable = True,
    )

    runfiles = ctx.runfiles(files = [augmented_dir] + ctx.files.data)
    for d in ctx.attr.data:
        runfiles = runfiles.merge(d[DefaultInfo].default_runfiles)

    return [
        DefaultInfo(
            executable = script,
            files = depset([script]),
            runfiles = runfiles,
        ),
    ]

quarkus_runner = rule(
    implementation = _quarkus_runner_impl,
    executable = True,
    attrs = {
        "augmented_app": attr.label(
            mandatory = True,
            doc = "The quarkus_bootstrap target producing the augmented app directory",
        ),
        "jvm_flags": attr.string_list(
            default = [],
            doc = "JVM flags to pass when running the application",
        ),
        "data": attr.label_list(
            default = [],
            allow_files = True,
            doc = "Runtime data files (e.g. config files) made available in runfiles",
        ),
    },
    doc = """
    Creates an executable shell script that runs a Quarkus augmented application.

    Example:
        quarkus_runner(
            name = "my-app-runner",
            augmented_app = ":my-app_augmented",
            jvm_flags = ["-Xmx512m", "-Dquarkus.http.port=8080"],
        )

    Usage:
        bazel run :my-app-runner
        bazel run :my-app-runner -- --debug   # JDWP on port 5005, suspended
        DEBUG=1 DEBUG_SUSPEND=n bazel run :my-app-runner
    """,
)
