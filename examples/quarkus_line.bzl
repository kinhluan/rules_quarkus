"""Repository exposing line-dependent labels to the examples.

Some APIs moved to new Maven coordinates between Quarkus lines, so their
`@maven` target names differ. Jakarta CDI is `jakarta.enterprise:jakarta.enterprise.cdi-api`
up to 4.1 and `jakarta.cdi:jakarta.cdi-api` from 5.0 (Quarkus 4). BUILD files
can't read QUARKUS_LINE from MODULE.bazel, so this repository gives them stable
aliases instead:

    deps = ["@quarkus_line//:cdi_api"]
"""

def _quarkus_line_impl(rctx):
    rctx.file("BUILD.bazel", """\
alias(
    name = "cdi_api",
    actual = "{cdi_api}",
    visibility = ["//visibility:public"],
)
""".format(cdi_api = rctx.attr.cdi_api))
    rctx.file("defs.bzl", 'QUARKUS_LINE = "{}"\n'.format(rctx.attr.line))

quarkus_line = repository_rule(
    implementation = _quarkus_line_impl,
    attrs = {
        "cdi_api": attr.string(mandatory = True, doc = "Label of the Jakarta CDI API target"),
        "line": attr.string(mandatory = True, doc = "Selected Quarkus line"),
    },
)
