"""
Public API for rules_quarkus.

This file re-exports the main macros and rules for building Quarkus applications.
"""

load("//quarkus:quarkus.bzl", "quarkus_application", "quarkus_library")

# Re-export main macros
quarkus_application = quarkus_application
quarkus_library = quarkus_library