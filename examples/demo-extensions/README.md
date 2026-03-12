# Demo Extensions Showcase

This example application demonstrates various Quarkus extensions (REST, Jackson, Mutiny, Health, Prometheus) built with **rules_quarkus** using Bazel.

## Features

- **Tier 1 (Core):** REST (JAX-RS), Jackson (JSON), Mutiny (Reactive), Vert.x HTTP.
- **Tier 4 (Observability):** SmallRye Health Checks, Micrometer (Prometheus Metrics).

## Prerequisites

- [Bazel](https://bazel.build/install) 7.x+
- [Java](https://adoptium.net/) 21

## Build and Run

### Build the application

```bash
# From the project root
bazel build //examples/demo-extensions:demo-extensions
```

### Run the application

```bash
# From the project root
./bazel-bin/examples/demo-extensions/demo-extensions
```

By default, the application will start on port `8080`.

## Verifying the Demo

Once the application is running, you can test the following endpoints:

### 1. REST & JSON API
```bash
curl http://localhost:8080/users
```

### 2. Observability (Health Checks)
```bash
curl http://localhost:8080/q/health
```

### 3. Observability (Prometheus Metrics)
```bash
curl http://localhost:8080/q/metrics
```

### 4. Custom Resource
```bash
curl http://localhost:8080/api/observability/info
```

## Project Structure

- `src/main/java/com/example/resource/`: REST resources.
- `src/main/java/com/example/health/`: Custom health checks.
- `src/main/resources/application.properties`: Quarkus configuration.
- `BUILD.bazel`: Bazel build definition using `quarkus_application`.
