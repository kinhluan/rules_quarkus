# AGENTS.md

Guide for AI coding agents (and people) working **on** this repository. For how to **use** the rules,
read [README.md](README.md).

## What this is

Bazel rules that compile a Quarkus app, run Quarkus augmentation as a Bazel action, and launch the
resulting fast-jar. Goals, in order: correct and cacheable in large monorepos, fast incremental builds,
small and readable code. Keep changes in that spirit: prefer a short rule users can read over a
configurable one.

## Layout

| Path | What |
|---|---|
| `quarkus/defs.bzl` | Public API. Re-exports `quarkus_application` and `quarkus_library`. |
| `quarkus/quarkus.bzl` | The macros. They create `<name>_lib` (java_library), `<name>_augmented` (augmentation) and `<name>` (runner). |
| `quarkus/quarkus_bootstrap.bzl` | Augmentation rule: builds the args/param file and runs `//tools:bootstrap_augmentor` as a worker. |
| `quarkus/quarkus_runner.bzl` | Bash launcher: resolves the app from runfiles and runs `QuarkusEntryPoint` on `lib/boot/*`. |
| `tools/src/main/java/io/quarkus/bazel/bootstrap/` | The augmentor (Java). Entry is `BootstrapAugmentor`, worker loop in `WorkerLoop`. |
| `tools/src/test/` | JUnit 5 tests (`java_test_suite` from contrib_rules_jvm, a dev dependency). |
| `examples/` | `hello-world` and `demo-extensions`. These are the integration tests. |
| `e2e/consumer/` | A separate Bazel module that depends on rules_quarkus as users do (`local_path_override`). Ignored by the root workspace (`.bazelignore`); build it from its own directory. |
| `examples/quarkus_line.bzl` | Repo rule exposing per-line labels, e.g. `@quarkus_line//:cdi_api`. |
| `MODULE.bazel` | Pinned Maven deps. `_PROFILES` holds one profile per Quarkus line, picked by `QUARKUS_LINE`. |
| `.github/workflows/ci.yml` | Matrix over Quarkus lines: build, test, smoke-run hello-world. |

## Commands

```bash
bazel build //tools/... //examples/...
bazel test //...                                 # all unit tests, also builds the examples
bazel run //examples/hello-world                 # then: curl localhost:8080/hello
```

To try the Quarkus 4 line, set `QUARKUS_LINE = "4"` in `MODULE.bazel`, run the commands, and revert
before committing. CI does the same edit with `sed`, so keep that assignment on one line, in exactly
that form.

Bazel 8.8.2 (`.bazelversion`) and Java 21 by default. CI also runs Bazel 7 and 9 and Java 25
(`USE_BAZEL_VERSION=9.3.2 bazel ...` with bazelisk). `MODULE.bazel.lock` is gitignored: its format
differs between Bazel versions.

## Rules of the road

- **Never add Maven artifacts to the user's `@maven`.** The non-dev `maven` extension usage in `MODULE.bazel`
  only calls `use_repo(maven, "maven")`. Every `install`/`artifact` tag goes on `maven_dev`
  (`dev_dependency = True`). `//tools` may depend only on `quarkus-bootstrap-core`, `quarkus-bootstrap-app-model`
  and `jboss-logging` from `@maven`; adding a fourth one is a breaking change for every user.
  `e2e/consumer` and its CI job check this.
- **Augmentation never downloads.** `BootstrapAugmentor` sets `setOffline(true)`. A missing jar is a build error.
- **Don't make consumers load dev tooling.** Anything users depend on (`//quarkus`, `//tools:bootstrap_augmentor`
  and its deps) must not load `contrib_rules_jvm` or other dev dependencies. Tests live in
  `//tools/src/test` for that reason.
- **Versions come from the profile.** Never hard-code a Quarkus, Vert.x, Jandex, ASM or Quarkiverse version
  outside `_PROFILES`. A new extension needs an entry in **every** profile (or `None` plus a conditional
  declaration, like Unleash).
- **Labels that differ per line go through `@quarkus_line`.** Example BUILD files can't read `QUARKUS_LINE`.
- **Worker stdout is the protocol.** In the worker, `System.out` carries JSON responses. `WorkerLoop` redirects
  it into a buffer per request. Don't cache `System.out` in a static field or write to the real stdout.
- **Augmentation must stay deterministic.** Same inputs, same output bytes. Otherwise remote and disk caches
  miss. No timestamps, no iteration over unordered maps when writing files.
- **Runfiles paths.** A `short_path` starting with `../` is an external repo (`<repo>/...`). Anything else is
  under `_main/`. See `_runfiles_path` in `quarkus_runner.bzl`.
- **The runner puts only `lib/boot/` on the classpath.** Adding `lib/main/` loads app classes twice.
- **The runner uses the JDK from runfiles** (`@rules_java//toolchains:current_java_runtime`), not `java` on PATH.
- **Supported Bazel: 7.7.1+, 8.6+, 9** (floor set by rules_jvm_external 7). Load every rule from its ruleset (`@rules_java//java:defs.bzl`, ...); Bazel 9
  has no native rule autoloading. Don't use WORKSPACE.

## Adding an extension

1. Add the runtime artifact and its `-deployment` artifact to `maven.install` in `MODULE.bazel`.
   Quarkiverse versions go in `_PROFILES`.
2. Use it in `examples/demo-extensions` with an endpoint that exercises it.
3. Run `bazel test //...` and `bazel run //examples/demo-extensions` on every line.
4. Move it to "Augmented in CI" in the README's extension table.

## Adding a Quarkus line

1. Add a profile to `_PROFILES` with versions taken from that Quarkus release's BOM.
2. Add the line to the CI matrix in `ci.yml`. Start it as `experimental: true`.
3. Note it in `CHANGELOG.md` under `[Unreleased]`.

## Changes and PRs

- Conventional Commits: `feat:`, `fix:`, `docs:`, `chore(release):`.
- Add user-visible changes to `CHANGELOG.md` under `[Unreleased]`, and list breaking changes under `### Breaking`.
- Keep README snippets in sync with `examples/hello-world`. The example is what CI tests.
- Never commit company-internal names, ticket IDs, hostnames or measurements from private systems. Examples
  and numbers must come from this repository.
