plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

group = "io.github.aeshen"
version = "0.1.0"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

val libs: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
    ?: throw IllegalArgumentException("No libs configured")

dependencies {
    implementation(gradleApi())

    implementation(libs.findLibrary("kotlin.gradle.plugin").get())
    implementation(libs.findLibrary("kotlin.serialization").get())

    implementation(libs.findLibrary("kotlinx.coroutines").get())
    implementation(libs.findLibrary("kotlinx.serialization").get())
    implementation(libs.findLibrary("kotlinx.serialization.json").get())
    implementation(libs.findLibrary("ksp.api").get())
    implementation(libs.findLibrary("ksp.gradle.plugin").get())
}

gradlePlugin {
    plugins {
        create("kotlinJvmConvention") {
            id = "kotlin-jvm-convention"
            implementationClass = "io.github.aeshen.KotlinJvmConventionPlugin"
            displayName = "Kotlin JVM Convention Plugin"
            description = "Applies common Kotlin/JVM conventions (stdlib, serialization, test libs, JDK toolchain, etc.)."
        }
    }
}

//publishing {
//    publications {
//        create<MavenPublication>("pluginMaven") {
//            // The `components["java"]` component contains the compiled plugin JAR
//            from(components["java"])
//            // Maven coordinates – you can change these as you wish
//            groupId = project.group.toString()
//            artifactId = "kotlin-jvm-convention-plugin"
//            version = project.version.toString()
//        }
//    }
//
//    // Example: publish to the local Maven cache (so other builds on the same
//    // machine can resolve it with `mavenLocal()`). Remove if you don’t need it.
//    repositories {
//        mavenLocal()
//    }
//}