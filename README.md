# rules_quarkus — Bazel Rules for Quarkus Applications

[![CI](https://github.com/kinhluan/rules_quarkus/actions/workflows/ci.yml/badge.svg)](https://github.com/kinhluan/rules_quarkus/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Bazel rules for building Quarkus applications with build-time augmentation support.

## Overview

`rules_quarkus` provides native Bazel support for building Quarkus applications, filling the gap where no official Bazel rules exist for the Quarkus framework. The project implements Quarkus's unique build-time augmentation process within Bazel's build system, enabling developers to use Bazel's powerful dependency management and caching while maintaining full Quarkus functionality.

## Quick Start

### Installation

Add the following to your `MODULE.bazel`:

```python
bazel_dep(name = "rules_quarkus", version = "0.1.0")

# Add Quarkus dependencies
maven = use_extension("@rules_jvm_external//:extensions.bzl", "maven")
maven.install(
    name = "maven",
    artifacts = [
        "io.quarkus:quarkus-arc:3.20.1",
        "io.quarkus:quarkus-rest:3.20.1",
        "io.quarkus:quarkus-vertx-http:3.20.1",
        "jakarta.ws.rs:jakarta.ws.rs-api:4.0.0",
        # ... add more dependencies as needed
    ],
)
```

### Basic Usage

Create a `BUILD.bazel` file:

```python
load("//quarkus:quarkus.bzl", "quarkus_application")

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
| Extension | Description | Version |
|-----------|-------------|---------|
| `quarkus-unleash` | Feature flags | 1.10.0 |
| `quarkus-langchain4j-*` | AI/LLM integration | 0.26.1 |
| `quarkus-tika` | Content analysis | 2.1.0 |

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

### Layer 3: Runtime
Executable application with fully processed Quarkus features:
- Fast startup time
- Low memory usage
- Build-time optimizations applied

## Requirements

| Component | Version |
|-----------|---------|
| [Bazel](https://bazel.build/install) | 7.x+ |
| [Java](https://adoptium.net/) | 21 |
| [Quarkus](https://quarkus.io) | 3.20.1 |

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
- `deps`: Regular dependencies (non-Quarkus libraries)
- `runtime_extensions`: Quarkus runtime extension modules
- `deployment_extensions`: Quarkus deployment modules
- `main_class`: **Required**. Set to your `@QuarkusMain` class or the main class from `pom.xml`. Use `"io.quarkus.runner.GeneratedMain"` for standard applications.
- `jvm_flags`: JVM flags for running the application

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