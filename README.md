<p align="center">
  <img src="assets/icon-512.png" alt="rules_quarkus logo" width="200">
</p>

# rules_quarkus — Bazel Rules for Quarkus Applications

[![CI](https://github.com/kinhluan/rules_quarkus/actions/workflows/ci.yml/badge.svg)](https://github.com/kinhluan/rules_quarkus/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Bazel rules for building Quarkus applications with build-time augmentation support.

## Overview

`rules_quarkus` provides native Bazel support for building Quarkus applications, focused on **large monorepos with trunk-based development**: fast incremental augmentation, a small API that is easy to read, and extension points to add your own features. The project implements Quarkus's unique build-time augmentation process within Bazel's build system, enabling developers to use Bazel's powerful dependency management and caching while maintaining full Quarkus functionality.

## Quick Start

### Installation

> **Not on the Bazel Central Registry.** The BCR module named `rules_quarkus` is a different project,
> [quarkusio/quarkus-bazel-rules](https://github.com/quarkusio/quarkus-bazel-rules). A plain
> `bazel_dep(name = "rules_quarkus", ...)` resolves to that one. Install this project with
> `git_override` as below. Don't depend on both projects in one workspace: they share the module name.

Add the following to your `MODULE.bazel`:

```python
bazel_dep(name = "rules_quarkus", version = "0.3.0")
git_override(
    module_name = "rules_quarkus",
    remote = "https://github.com/kinhluan/rules_quarkus.git",
    tag = "v0.3.0",  # or: commit = "<sha>" to pin an unreleased revision
)

bazel_dep(name = "rules_jvm_external", version = "6.6")

# Add Quarkus dependencies
maven = use_extension("@rules_jvm_external//:extensions.bzl", "maven")
maven.install(
    name = "maven",
    artifacts = [
        "io.quarkus:quarkus-arc:3.40.1.2",
        "io.quarkus:quarkus-rest:3.40.1.2",
        "io.quarkus:quarkus-vertx-http:3.40.1.2",
        "jakarta.ws.rs:jakarta.ws.rs-api:4.0.0",
        # ... add more dependencies as needed
    ],
)
```

`git_override` only takes effect in the root module. The `version` in `bazel_dep` must still be set,
but the override decides which code is fetched.

### Basic Usage

Create a `BUILD.bazel` file:

```python
load("@rules_quarkus//quarkus:defs.bzl", "quarkus_application")

quarkus_application(
    name = "my-app",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/resources/**/*"]),

    # Regular dependencies (APIs)
    deps = [
        "@maven//:jakarta_ws_rs_jakarta_ws_rs_api",
        "@maven//:jakarta_enterprise_jakarta_enterprise_cdi_api",
    ],

    # Quarkus runtime extensions
    runtime_extensions = [
        "@maven//:io_quarkus_quarkus_arc",
        "@maven//:io_quarkus_quarkus_rest",
        "@maven//:io_quarkus_quarkus_vertx_http",
    ],

    # Quarkus deployment modules (for build-time processing)
    deployment_extensions = [
        "@maven//:io_quarkus_quarkus_arc_deployment",
        "@maven//:io_quarkus_quarkus_rest_deployment",
        "@maven//:io_quarkus_quarkus_vertx_http_deployment",
    ],

    # Main class (required)
    main_class = "io.quarkus.runner.GeneratedMain",
)
```

Build and run:

```bash
# Build the application
bazel build //:my-app

# Run the application
./bazel-bin/my-app/my-app

# Access your endpoints
curl http://localhost:8080/hello
```

## Supported Extensions

`rules_quarkus` supports a wide range of Quarkus extensions organized in 5 tiers:

### Tier 1: Core (Essential)
| Extension | Description |
|-----------|-------------|
| `quarkus-arc` | CDI dependency injection |
| `quarkus-rest` | RESTful web services |
| `quarkus-rest-jackson` | JSON serialization |
| `quarkus-vertx-http` | HTTP server |
| `quarkus-mutiny` | Reactive programming |

### Tier 2: Database (Reactive)
| Extension | Description |
|-----------|-------------|
| `quarkus-reactive-oracle-client` | Reactive Oracle database client |
| `quarkus-reactive-mysql-client` | Reactive MySQL database client |
| `quarkus-redis-client` | Redis client |

### Tier 3: Messaging
| Extension | Description |
|-----------|-------------|
| `quarkus-messaging-kafka` | Apache Kafka messaging |
| `quarkus-messaging-rabbitmq` | RabbitMQ messaging |
| `quarkus-grpc` | gRPC services |

### Tier 4: Observability
| Extension | Description |
|-----------|-------------|
| `quarkus-micrometer-registry-prometheus` | Prometheus metrics |
| `quarkus-smallrye-health` | Health checks |

### Tier 5: Quarkiverse
| Extension | Description | Quarkus 3.x | Quarkus 4.x |
|-----------|-------------|-------------|-------------|
| `quarkus-unleash` | Feature flags | 1.15.0 | not yet available |
| `quarkus-langchain4j-*` | AI/LLM integration | 1.13.3 | 2.0.0.Beta4 |
| `quarkus-tika` | Content analysis | 2.3.2 | 4.0.0 |

## Architecture

`rules_quarkus` implements Quarkus's three-layer build architecture in Bazel:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Layer 1:      │    │   Layer 2:      │    │   Layer 3:      │
│   Compile       │───▶│   Augment       │───▶│   Runtime       │
│                 │    │                 │    │                 │
│ java_library    │    │ QuarkusBootstrap│    │ Executable      │
│ Standard        │    │ Build-time      │    │ Application     │
│ compilation     │    │ processing      │    │ with CDI, etc.  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### Layer 1: Compile
Standard Java compilation using Bazel's `java_library`. Source files are compiled to bytecode with minimal processing.

### Layer 2: Augment
Quarkus build-time augmentation using the official QuarkusBootstrap API:
- Jandex bytecode indexing
- CDI proxy generation
- Configuration processing
- Extension discovery
- Optimized bytecode generation

The augmentor runs as a Bazel **persistent worker**, so the JVM startup and Quarkus bootstrap
class loading are paid once per build instead of once per application. Bazel-built JARs that
share a filename (e.g. several `libbuild.jar`) are staged under unique names so none overwrites
another in `lib/main/`.

### Layer 3: Runtime
A `quarkus_runner` shell wrapper that resolves the augmented app from runfiles and launches it via
`QuarkusEntryPoint` on the `lib/boot/` classpath. Executable application with fully processed Quarkus features:
- Fast startup time
- Low memory usage
- Build-time optimizations applied

## Requirements

| Component | Version |
|-----------|---------|
| [Bazel](https://bazel.build/install) | 7.x+ |
| [Java](https://adoptium.net/) | 21 |
| [Quarkus](https://quarkus.io) | 3.40.1.2 (LTS, default) or 4.0.0 (experimental) |

### Quarkus version lines

`MODULE.bazel` carries one pinned dependency profile per Quarkus line (`_PROFILES`). Select it with:

```python
QUARKUS_LINE = "3"   # 3.40.1.2 LTS (default), or "4" for 4.0.0
```

Each profile pins the libraries Quarkus is sensitive to (Vert.x, Jandex, ASM, CDI API, Quarkiverse extensions) to the versions in that line's BOM. Pins win over transitive versions, so mixing lines (e.g. Vert.x 4 under Quarkus 4) breaks augmentation. CI builds and tests every line; 4.x is marked experimental. Unleash is not yet available for 4.x and is left out of that profile.

## Examples

We provide several example applications to help you get started with `rules_quarkus`:

### 🚀 Hello World
A minimal "Supersonic Subatomic" REST API showing the basic setup.
- **Location:** [`examples/hello-world/`](examples/hello-world/)
- **Build:** `bazel build //examples/hello-world:hello-world`
- **Run:** `./bazel-bin/examples/hello-world/hello-world`

### 🏗️ Demo Extensions Showcase
A comprehensive example demonstrating multi-tier extensions (REST, Jackson, Mutiny, Health, and Metrics).
- **Location:** [`examples/demo-extensions/`](examples/demo-extensions/)
- **Build:** `bazel build //examples/demo-extensions:demo-extensions`
- **Run:** `./bazel-bin/examples/demo-extensions/demo-extensions`

---

## Macros and Rules

### `quarkus_application`

Main macro for building Quarkus applications.

**Parameters:**
- `name`: Application name (Required)
- `srcs`: Java source files
- `resources`: Application resources (application.properties, etc.)
- `deps`: Compile-time dependencies (referenced by source code)
- `runtime_deps`: Runtime-only dependencies (JDBC drivers, serializers, ...). Not on the compile classpath, so ABI changes in them don't recompile the app
- `runtime_extensions`: Quarkus runtime extension modules
- `deployment_extensions`: Quarkus deployment modules
- `main_class`: **Required**. Set to your `@QuarkusMain` class or the main class from `pom.xml`. Use `"io.quarkus.runner.GeneratedMain"` for standard applications.
- `jvm_flags`: JVM flags for running the application
- `data`: Runtime data files (e.g. config files) made available to `bazel run`
- `plugins`: Annotation processor plugins (e.g. Lombok)

Labels listed in several of `deps` / `runtime_deps` / `runtime_extensions` are deduplicated.

**Debugging:** `bazel run //:my-app -- --debug` (or `DEBUG=1`) starts the app with JDWP on port 5005, suspended until a debugger attaches. Use `DEBUG_PORT` / `DEBUG_SUSPEND=n` to change that.

### `quarkus_library`

Creates a library that can be used as a dependency for Quarkus applications.

**Parameters:**
- `name`: Library name
- `srcs`: Java source files
- `resources`: Resources
- `deps`: Dependencies
- `extensions`: Quarkus extensions used by this library

## Resources

- [Architecting Deterministic Quarkus Builds: Native Support for Bazel](docs/blogs/2026-03-11-bazel-support-for-quarkus.md) — An in-depth look at the architecture and motivation behind `rules_quarkus`.
- [Deep Dive: Taming the Quarkus ClassLoader for Hermetic Bazel Builds](docs/blogs/2026-03-12-deep-dive-quarkus-classloader-bazel.md) — The technical journey of solving ClassLoader challenges and Quarkus Issue #52915.

See [CHANGELOG.md](CHANGELOG.md) for release notes and breaking changes.

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on:

- Setting up the development environment
- Building and testing changes
- Submitting pull requests
- Reporting issues

## License

Licensed under the [Apache License, Version 2.0](LICENSE).

## Authors

Created and maintained by:
- [@kinhluan](https://github.com/kinhluan)
- [@tructxn](https://github.com/tructxn)
- [@Nhannguyenus24](https://github.com/Nhannguyenus24)

---

> **Note:** This project is a community-driven effort and is not officially affiliated with Red Hat or the Quarkus project.
>
> This project is an evolution of the work originally started at [tructxn/rule-quarkus](https://github.com/tructxn/rule-quarkus).