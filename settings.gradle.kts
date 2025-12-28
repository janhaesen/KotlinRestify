rootProject.name = "kotlin-restify"

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

includeBuild("build-logic/kotlin-jvm-convention")
includeBuild("build-logic/ktlint-convention")

include(
    ":kotlinrestify-annotations",
    ":kotlinrestify-core",
    ":kotlinrestify-openapi",
    ":kotlinrestify-processor",
    ":kotlinrestify-retry",
)
