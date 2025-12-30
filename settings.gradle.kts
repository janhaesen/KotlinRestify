rootProject.name = "kotlin-restify"

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenLocal()
        mavenCentral()
    }
}

includeBuild("build-logic")

include(
    ":kotlinrestify-annotations",
    ":kotlinrestify-core",
    ":kotlinrestify-openapi",
    ":kotlinrestify-processor",
    ":kotlinrestify-retry",
)
