# Hello World Quarkus Application

A minimal "Supersonic Subatomic" REST API showing how to build and run a Quarkus application with Bazel using **rules_quarkus**.

## Features

- Basic JAX-RS Endpoint
- CDI (Arc) Dependency Injection
- Minimal dependency footprint

## Prerequisites

- [Bazel](https://bazel.build/install) 7.x+
- [Java](https://adoptium.net/) 21

## Build and Run

### Build the application

```bash
# From the project root
bazel build //examples/hello-world:hello-world
```

### Run the application

```bash
# From the project root
./bazel-bin/examples/hello-world/hello-world
```

By default, the application will start on port `8080`.

## Verifying the App

Once the application is running, you can test the hello endpoint:

```bash
curl http://localhost:8080/hello
```

Expected output: `Hello from RESTEasy Reactive` (or similar depending on your code).

## Project Structure

- `src/main/java/com/example/`: REST resource and business logic.
- `src/main/resources/application.properties`: Quarkus configuration.
- `BUILD.bazel`: Bazel build definition using `quarkus_application`.
