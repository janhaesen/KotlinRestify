rootProject.name = "kotlin-restify"

includeBuild("build-logic")

include(
    ":kotlinrestify-annotations",
    ":kotlinrestify-core",
    ":kotlinrestify-openapi",
    ":kotlinrestify-processor",
    ":kotlinrestify-retry",
)
