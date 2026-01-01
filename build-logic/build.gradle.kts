plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    alias(libs.plugins.ktlint.gradle.plugin)
}

group = "io.github.aeshen"
version = "0.1.0"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

val libs: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
    ?: throw IllegalArgumentException("No libs configured")

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(gradleApi())

    implementation(libs.findLibrary("kotlin.gradle.plugin").get())
    implementation(libs.findLibrary("kotlin.serialization").get())
    implementation(libs.findLibrary("kotlin.stdlib").get())

    implementation(libs.findLibrary("kotlinx.coroutines").get())
    implementation(libs.findLibrary("kotlinx.serialization").get())
    implementation(libs.findLibrary("kotlinx.serialization.json").get())

    implementation(libs.findLibrary("ksp.api").get())
    implementation(libs.findLibrary("ksp.gradle.plugin").get())

    implementation(libs.findLibrary("ktlint.gradle").get())
    implementation(libs.findLibrary("detekt.gradle.plugin").get())
}

gradlePlugin {
    plugins {
        create("ktlintConvention") {
            id = "ktlint-convention"
            implementationClass = "io.github.aeshen.KtlintConventionPlugin"
            displayName = "Ktlint Convention Plugin"
            description = "Applies org.jlleitschuh.gradle.ktlint with project‑wide defaults."
        }
        create("detektConvention") {
            id = "detekt-convention"
            implementationClass = "io.github.aeshen.DetektConventionPlugin"
            displayName = "Detekt Convention Plugin"
            description = "Applies Detekt configuration for clean code."
        }
        create("kotlinJvmConvention") {
            id = "kotlin-jvm-convention"
            implementationClass = "io.github.aeshen.KotlinJvmConventionPlugin"
            displayName = "Kotlin JVM Convention Plugin"
            description = "Applies common Kotlin/JVM conventions (stdlib, serialization, test libs, JDK toolchain, etc.)."
        }
        create("localPublishConvention") {
            id = "local-publish-convention"
            implementationClass = "io.github.aeshen.LocalPublishConventionPlugin"
            displayName = "Local Publish Convention"
            description = "Configures maven-publish (single publication from components['java']), sources/javadoc jars and mavenLocal repository."
        }
        create("dependencyRulesConvention") {
            id = "dependency-rules-convention"
            implementationClass = "io.github.aeshen.DependencyRulesPlugin"
            displayName = "Dependency rule Convention"
            description = "Ensure non compliant dependencies are reported and have the build fail."
        }
    }
}
