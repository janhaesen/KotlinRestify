dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(
                files(
                    settingsDir.resolve("../gradle/libs.versions.toml")
                )
            )
        }
    }
}
