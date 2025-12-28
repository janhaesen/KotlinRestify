plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
    alias(libs.plugins.ktlint.gradle.plugin)
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.ktlint.gradle)
}

// Publish the convention plugin
gradlePlugin {
    plugins {
        create("ktlintConvention") {
            id = "ktlint-convention"
            implementationClass = "io.github.aeshen.KtlintConventionPlugin"
            displayName = "Ktlint Convention Plugin"
            description = "Applies org.jlleitschuh.gradle.ktlint with project‑wide defaults."
        }
    }
}
