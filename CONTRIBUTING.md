# Contributing to rules_quarkus

First off, thank you for considering contributing to `rules_quarkus`! We are on a mission to bring **Supersonic, Subatomic, Fast and Correct** builds to the Quarkus ecosystem.

This project bridges the gap between the Quarkus build-time engine and Bazel's hermetic build system. Because of this unique intersection, contributions require an understanding of both worlds.

---

## 🛠 Quick Start

### Prerequisites
- **Bazel 7.x+**: We use Bzlmod for dependency management.
- **Java 21**: The project is optimized for modern LTS Java.
- **Quarkus 3.20.x**: Our current target version.

### Building & Testing
We use our own `examples/` directory as the primary integration test suite.

```bash
# Build the core tools
bazel build //tools/...

# Run the Hello World example
bazel run //examples/hello-world

# Run the comprehensive Extensions Demo (Tier 1-5)
bazel run //examples/demo-extensions
```

---

## 🏗 Architecture Guide

When contributing code, please respect our **Three-Layer Architecture**. This separation is what ensures Bazel's hermeticity:

1.  **Layer 1: Compile (`java_library`)**
    *   Standard bytecode compilation.
    *   No Quarkus magic here, just pure `javac`.
2.  **Layer 2: Augment (`quarkus_bootstrap`)**
    *   The "Heavy Lifting". Uses the official `QuarkusBootstrap` API.
    *   Generates CDI proxies, Jandex indexes, and optimized bytecode.
    *   *Where to look:* `quarkus/quarkus_bootstrap.bzl` and `tools/src/main/java/io/quarkus/bazel/bootstrap/`.
3.  **Layer 3: Runtime (`quarkus_runner`)**
    *   Resolves the `quarkus-app` structure from Bazel `runfiles`.
    *   Launches the app via `QuarkusEntryPoint`.
    *   *Where to look:* `quarkus/quarkus_runner.bzl`.

---

## 📦 Adding Support for New Extensions

We organize extension support into 5 Tiers. If you want to add a new extension:

1.  **Check the Tier:** Does it fit into Database (Tier 2), Messaging (Tier 3), or Quarkiverse (Tier 5)?
2.  **Update `MODULE.bazel`:** Add both the `runtime` and `deployment` artifacts to the `maven.install` section.
3.  **Add to `examples/demo-extensions`:** Create a simple endpoint in the demo app to verify the extension works under Bazel's classloader.
4.  **Verify Transitive Deps:** Quarkus deployment modules often have complex transitive dependencies. Use `exclusions` in `MODULE.bazel` if you encounter circular dependency errors.

---

## 📝 Development Workflow

1.  **Check the Plan:** Review `UPGRADE_PLAN.md` to see current priorities and avoid duplicate work.
2.  **Fork & Branch:** Use descriptive branch names like `feat/tier-3-kafka` or `fix/classloader-issue`.
3.  **Surgical Changes:** We prefer targeted fixes. If you're refactoring core logic, please explain the "Why" in the PR.
4.  **Testing is Mandatory:** Every PR must be verified against at least one example project. Provide the `bazel run` output in your PR description.

---

## 🤝 Submitting Pull Requests

*   **Commit Messages:** Use clear, imperative titles (e.g., "Add support for Redis reactive client").
*   **PR Template:**
    *   **Goal:** What problem does this solve?
    *   **Technical Approach:** How did you implement it? (Especially if you modified the `BootstrapAugmentor`).
    *   **Verification:** Show us that `bazel run //examples/...` works.

## ⚖️ License

By contributing, you agree that your contributions will be licensed under the **Apache License, Version 2.0**.

---
*Stay Supersonic.*
