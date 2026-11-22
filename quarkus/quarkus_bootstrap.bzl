"""
Quarkus Bootstrap Augmentation Rule (Approach 2)

This rule implements Quarkus augmentation using the QuarkusBootstrap API.
It takes compiled application classes and runs the full Quarkus augmentation
pipeline to generate CDI proxies, optimized bytecode, and more.
"""

load("@rules_java//java:defs.bzl", "JavaInfo")

def _quarkus_bootstrap_impl(ctx):
    """
    Implementation of quarkus_bootstrap rule.

    This rule:
    1. Collects all JAR files from dependencies
    2. Runs BootstrapAugmentor with QuarkusBootstrap API
    3. Outputs the augmented application directory
    """

    # Output directory for augmented application
    output_dir = ctx.actions.declare_directory(ctx.label.name + "-quarkus-app")

    # Collect application JARs: DIRECT output JARs only (not transitive deps).
    # Only the main service's own compiled code becomes the "app artifact".
    # Using java_outputs gives us the class JAR produced by each application target,
    # without pulling in transitive deps (which would create a fat app JAR and
    # cause class duplication with quarkus/transformed-bytecode.jar).
    application_jar_set = {}
    application_jars = []
    for dep in ctx.attr.application:
        if JavaInfo in dep:
            for output in dep[JavaInfo].java_outputs:
                jar = output.class_jar
                if jar and jar.path not in application_jar_set:
                    application_jar_set[jar.path] = True
                    application_jars.append(jar)

    # Collect runtime JARs from ALL sources (for the ApplicationModel + augment CL).
    # This includes transitive deps from both application targets and runtime_deps,
    # ensuring proto stubs, utility libs, and other transitive deps are available.
    # IMPORTANT: Exclude application JARs to prevent duplication — the app artifact
    # is already registered separately in the ApplicationModel. Without this filter,
    # @QuarkusMain classes appear twice and MainClassBuildStep fails.
    runtime_jar_set = {}
    runtime_jars = []
    runtime_depsets = []
    for dep in ctx.attr.application + ctx.attr.runtime_deps:
        if JavaInfo in dep:
            runtime_depsets.append(dep[JavaInfo].transitive_runtime_jars)

    if runtime_depsets:
        for jar in depset(transitive = runtime_depsets).to_list():
            if jar.path not in runtime_jar_set and jar.path not in application_jar_set:
                runtime_jar_set[jar.path] = True
                runtime_jars.append(jar)

    # Collect deployment JARs (Quarkus deployment modules + their transitive dependencies).
    #
    # This list used to also repeat every transitive dep of runtime_deps, so that extension
    # processors would find things like commons-lang3 on the augmentation classpath. That is
    # already covered: ApplicationModelFactory.addAllDependencies flags EVERY runtime JAR with
    # RUNTIME_CP | DEPLOYMENT_CP, precisely because the augmentation classloader is built from
    # ApplicationModel.getDependencies() (deployment-flagged deps only). Passing the same JARs
    # again under --deployment-jars only made the second loop there hit its dedup `continue`.
    # Dropping the repeat also shortens the param file and removes bogus model entries: the
    # collision-staged JARs were being registered twice, once under their staged path from
    # the runtime list and once under their original path from this one.
    #
    # Action inputs are unaffected: those JARs still arrive via runtime_jars below.
    deployment_jar_set = {}
    deployment_jars = []
    deployment_depsets = []

    for dep in ctx.attr.deployment_deps:
        if JavaInfo in dep:
            deployment_depsets.append(dep[JavaInfo].transitive_runtime_jars)

    if deployment_depsets:
        for jar in depset(transitive = deployment_depsets).to_list():
            if jar.path not in deployment_jar_set:
                deployment_jar_set[jar.path] = True
                deployment_jars.append(jar)

    # Build command arguments
    args = ctx.actions.args()
    args.add("--output-dir", output_dir.path)
    args.add("--app-name", ctx.attr.application_name)

    if ctx.attr.main_class:
        args.add("--main-class", ctx.attr.main_class)

    # Add JAR lists (comma-separated paths).
    # add_joined keeps the wire format identical to the old ",".join(...) but defers the join
    # to execution time in native code, so analysis no longer builds and retains a large
    # string per application target.
    args.add_joined("--application-jars", application_jars, join_with = ",")
    args.add_joined("--runtime-jars", runtime_jars, join_with = ",")
    args.add_joined("--deployment-jars", deployment_jars, join_with = ",")

    # Use param file to avoid ARG_MAX with large dependency sets
    args.use_param_file("@%s", use_always = True)

    # All input files
    inputs = depset(
        direct = application_jars,
        transitive = [
            depset(runtime_jars),
            depset(deployment_jars),
        ],
    )

    # Run augmentor
    ctx.actions.run(
        outputs = [output_dir],
        inputs = inputs,
        executable = ctx.executable._augmentor,
        arguments = [args],
        mnemonic = "QuarkusBootstrap",
        progress_message = "Running Quarkus augmentation for %s" % ctx.label.name,
        # A large part of each augmentation is JVM startup plus loading the Quarkus bootstrap
        # classes - a fixed cost otherwise paid once per application. A worker JVM pays it once
        # for the whole build and keeps the JIT warm.
        #
        # JSON rather than the default protobuf protocol: protobuf would have to go on the
        # augmentor's system classloader, where it would collide with the protobuf the
        # augmented services carry themselves.
        #
        # Nothing here passes --persistent_worker: Bazel appends it to the worker's startup
        # command itself, and omits it when a non-worker strategy runs the same action. That
        # is what lets WorkerLoop.isWorkerStartup tell the two apart. The param file below is
        # mandatory for workers - Bazel expands it into each WorkRequest's arguments.
        execution_requirements = {
            "supports-workers": "1",
            "requires-worker-protocol": "json",
        },
    )

    # Create output JAR reference for downstream targets
    # The main JAR is in output_dir/quarkus-run.jar
    return [
        DefaultInfo(
            files = depset([output_dir]),
            runfiles = ctx.runfiles(files = [output_dir]),
        ),
    ]

quarkus_bootstrap = rule(
    implementation = _quarkus_bootstrap_impl,
    attrs = {
        "application": attr.label_list(
            providers = [JavaInfo],
            mandatory = True,
            doc = "Application library targets (compiled user code)",
        ),
        "runtime_deps": attr.label_list(
            providers = [JavaInfo],
            default = [],
            doc = "Runtime dependencies (Quarkus extensions, other libs)",
        ),
        "deployment_deps": attr.label_list(
            providers = [JavaInfo],
            default = [],
            doc = "Deployment dependencies (Quarkus *-deployment modules)",
        ),
        "application_name": attr.string(
            mandatory = True,
            doc = "Application name",
        ),
        "main_class": attr.string(
            default = "io.quarkus.runner.GeneratedMain",
            doc = "Main class (usually auto-generated by Quarkus)",
        ),
        "_augmentor": attr.label(
            default = Label("//tools:bootstrap_augmentor"),
            executable = True,
            cfg = "exec",
            doc = "Bootstrap augmentor tool",
        ),
    },
    doc = """
    Runs Quarkus augmentation using QuarkusBootstrap API.

    This rule takes compiled application code and runs the full Quarkus
    augmentation pipeline, including:
    - Jandex indexing
    - CDI bean discovery and proxy generation
    - REST endpoint registration
    - Configuration processing
    - Bytecode optimization

    Output is a directory containing the complete quarkus-app structure.

    Example:
        quarkus_bootstrap(
            name = "my-app-augmented",
            application = [":my-app-lib"],
            runtime_deps = [
                "@maven//:io_quarkus_quarkus_arc",
                "@maven//:io_quarkus_quarkus_resteasy_reactive",
            ],
            deployment_deps = [
                "@maven//:io_quarkus_quarkus_arc_deployment",
                "@maven//:io_quarkus_quarkus_resteasy_reactive_deployment",
            ],
            application_name = "my-app",
        )
    """,
)
