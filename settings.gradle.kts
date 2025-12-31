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
    ":annotations",
    ":core",
    ":openapi",
    ":processor",
    ":sample",
)
