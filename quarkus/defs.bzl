"""
Public API for rules_quarkus.

This file re-exports the main macros and rules for building Quarkus applications.
"""

load(
    "//quarkus:quarkus.bzl",
    _quarkus_application = "quarkus_application",
    _quarkus_library = "quarkus_library",
)

# Re-export main macros. Loaded names are file-local and can't be re-assigned under the
# same name ("conflicting global declaration"), hence the underscore aliases.
quarkus_application = _quarkus_application
quarkus_library = _quarkus_library
