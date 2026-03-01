# Contributing to rules_quarkus

Thank you for your interest in contributing to rules_quarkus! This document provides guidelines for contributing to the project.

## Quick Start

### Prerequisites

- [Bazel 7.x+](https://bazel.build/install)
- [Java 21](https://adoptium.net/)

### Building

```bash
# Build hello-world example
bazel build //examples/hello-world:hello-world-lib

# Build all targets
bazel build //...
```

### Testing

```bash
# Test hello-world example (compile only)
bazel test //examples/hello-world:hello-world-lib

# Run the application
bazel build //examples/hello-world:hello-world
./bazel-bin/examples/hello-world/hello-world
```

## Development Guidelines

### Code Style

- Follow existing code conventions in .bzl files and Java code
- Add documentation for new macros and rules
- Update examples when adding new features

### Testing Changes

Before submitting a PR:

1. Verify examples build and run correctly
2. Test with different Quarkus extensions
3. Run `bazel build //...` to ensure no build breakage

## Submitting PRs

1. Fork the repository
2. Create a feature branch from `main`
3. Make your changes with clear commit messages
4. Test your changes thoroughly
5. Submit a pull request with:
   - Clear description of changes
   - Testing evidence (build output, screenshots)
   - Reference to any related issues

## Reporting Issues

When reporting issues, please include:

- Bazel version (`bazel version`)
- Java version (`java -version`)
- Minimal reproduction case
- Build logs and error messages

## Architecture

The project uses a three-layer architecture:

1. **Compile**: Standard `java_library` compilation
2. **Augment**: QuarkusBootstrap API for build-time processing
3. **Runtime**: Executable application with augmented classes

See `docs/PLAN.md` for detailed technical information.

## License

By contributing, you agree that your contributions will be licensed under the Apache 2.0 License.