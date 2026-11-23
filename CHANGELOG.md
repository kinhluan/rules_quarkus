# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project uses [Semantic Versioning](https://semver.org/) (pre-1.0: minor versions may break).

## [Unreleased]

### Added

- Bazel 7, 8 and 9 and Java 21 and 25 are supported and tested in CI. `MODULE.bazel` declares
  `bazel_compatibility = [">=7.7.1"]`.
- `AGENTS.md`: repository guide for AI coding agents (layout, commands, invariants). `CLAUDE.md` imports it.

### Changed

- **The launcher runs on the Bazel-selected JDK** (`--java_runtime_version`), shipped in its runfiles,
  instead of `java` from `PATH`. It falls back to `PATH` only if no JDK is found in runfiles.
- rules_java 8.14.0 → 9.9.0 (provides `remotejdk_25`). Development Bazel 7.6.1 → 8.8.2.
- **rules_jvm_external 6.6 → 7.2.** 6.x pulls in rules_android 0.6, which uses the native `CcInfo` removed
  in Bazel 9. 7.x requires Bazel 7.7.1+, 8.6+ or 9: update `rules_jvm_external` in your own `MODULE.bazel` too.
- contrib_rules_jvm (dev only) 0.27.0 → 0.34.0: works on Bazel 9 and no longer passes
  `-Djava.security.manager=allow`, which JDK 24+ rejects at start-up.
- `.bazelrc`: `--experimental_reuse_sandbox_directories` → `--reuse_sandbox_directories`.
- CI matrix: Quarkus line × Java on the default Bazel, plus Bazel 7 and 9 on the default line and Java.
  The smoke run checks that the app started on the expected JDK.
- README rewritten: status table (supported / planned), Mermaid architecture diagram, and an
  extension table that separates extensions augmented in CI from those that are only pinned.
  Fixed the launcher path in the quick start (`bazel-bin/my-app`).

## [0.3.0] - 2026-11-22

### Added

- **Quarkus version lines.** `MODULE.bazel` selects a pinned dependency profile with
  `QUARKUS_LINE`: `"3"` (Quarkus 3.40.1.2 LTS, default) or `"4"` (Quarkus 4.0.0, experimental).
  Each profile pins Vert.x, Jandex, ASM, the CDI API and Quarkiverse extensions to that line's BOM.
  On 4.x the CDI API comes from its new `jakarta.cdi:jakarta.cdi-api` coordinates; examples
  depend on `@quarkus_line//:cdi_api`, an alias that resolves to the right label per line.
- **Persistent worker** for augmentation (JSON worker protocol). The JVM startup and Quarkus
  bootstrap cost is paid once per build instead of once per application.
- `quarkus_application` attributes: `runtime_deps` (runtime-only deps, kept off the compile
  classpath), `data` (runtime files for `bazel run`) and `plugins` (annotation processors).
- Remote debugging in the runner: `--debug` or `DEBUG=1` (JDWP on port 5005, suspended).
  `DEBUG_PORT` and `DEBUG_SUSPEND=n` change the port and suspension.
- Unit tests for the augmentor (`bazel test //tools/src/test:tests`), run in CI.
- CI matrix over Quarkus 3.x and 4.x, plus a smoke run of the hello-world app (`GET /hello`).

### Changed

- Quarkus 3.20.1 → 3.40.1.2, rules_java 8.6.2 → 8.14.0, rules_jvm_external 6.5 → 6.6.
  Quarkiverse: unleash 1.10.0 → 1.15.0, langchain4j 0.26.1 → 1.13.3, tika 2.1.0 → 2.3.2.
- `maven.install` uses `version_conflict_policy = "pinned"`: explicit pins win over transitive versions.
- Only the application's own class JAR is the application artifact. Its transitive dependencies
  become runtime dependencies, and the application JAR is excluded from them. This fixes duplicate
  `@QuarkusMain` classes failing `MainClassBuildStep`.
- Bazel-built JARs sharing a filename (e.g. several `libbuild.jar`) are staged under unique names
  so that none overwrites another in `lib/main/`.
- Internal Bazel JARs are registered with `quarkus.index-dependency` so their CDI beans are discovered.
- Runtime JARs are no longer repeated in `--deployment-jars`; the augmentor always uses a param file.
- A failure to copy a boot JAR now fails the build instead of producing an app that dies at startup.
- `quarkus_runner` resolves the augmented app correctly when the rules are used from another repository.
- Labels repeated across `deps`, `runtime_deps` and `runtime_extensions` are deduplicated.
- README: install with `git_override` (the BCR module named `rules_quarkus` is a different project),
  and load from `@rules_quarkus//quarkus:defs.bzl`.

### Breaking

- **The runner launches from `lib/boot/` only.** `lib/main/` is no longer on the system classpath;
  Quarkus' `RunnerClassLoader` loads it. Putting it on the system classpath loaded application
  classes twice.
- **The runner no longer adds `-Djava.util.logging.manager=org.jboss.logmanager.LogManager`.**
  Add it to `jvm_flags` if your application relies on it (both examples do).
- `--debug` is consumed by the runner and is no longer passed to the application.
- The augmentor system classpath no longer contains `quarkus-core`, `quarkus-core-deployment`,
  `arc` and `arc-deployment`; they come from the application model only. This avoids the same
  class being loaded by both the system and the augmentation classloader.

### Removed

- Placeholder test sources `HelloWorldTest` and `TestDeps`.

## [0.2.0] - 2026-03-12

### Added

- Community documentation, technical blog posts and CI workflow.

### Fixed

- Classloader fixes in the augmentor.
- `Argument list too long` on large classpaths (param files).
- Missing imports in `ConfigParser`.

## [0.1.0] - 2026-03-01

### Added

- `quarkus_application` and `quarkus_library` macros: compile, augment via the QuarkusBootstrap API, run.
- Quarkus 3.20.1 with five tiers of extensions (core, reactive database, messaging, observability, Quarkiverse).
- `hello-world` and `demo-extensions` examples.

### Fixed

- Replaced `native.sh_binary` (removed in Bazel 9); pinned Bazel 7.6.1.

[Unreleased]: https://github.com/kinhluan/rules_quarkus/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/kinhluan/rules_quarkus/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/kinhluan/rules_quarkus/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/kinhluan/rules_quarkus/releases/tag/v0.1.0
