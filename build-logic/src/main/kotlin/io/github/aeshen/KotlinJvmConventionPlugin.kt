package io.github.aeshen

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.Actions.with
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * A tiny convention plugin that applies a common set of Kotlin/JVM settings:
 *
 *  - Kotlin JVM plugin
 *  - Kotlin serialization plugin (optional – you can comment it out)
 *  - Maven Central repository
 *  - Stdlib, serialization, coroutines, and test dependencies
 *  - JDK toolchain
 *  - JUnit‑5 test configuration
 */
class KotlinJvmConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val libs = extensions.getByType<VersionCatalogsExtension>()
                .named("libs")

            pluginManager.apply(KotlinPluginWrapper::class.java)
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            pluginManager.apply("com.google.devtools.ksp")

            repositories {
                mavenCentral()
                mavenLocal()
            }

            dependencies {
                // Kotlin stdlib (JVM)
                add("implementation", libs.findLibrary("kotlin.stdlib").get())

                // Serialization (JSON) – used by many modules
                add("implementation", libs.findLibrary("kotlinx.serialization").get())
                add("implementation", libs.findLibrary("kotlinx.serialization.json").get())

                // Coroutines – handy for async HTTP, DB, etc.
                add("implementation", libs.findLibrary("kotlinx.coroutines").get())

                // Test libraries (JUnit 5 + Kotlin test)
                add("testImplementation", libs.findLibrary("kotlin.test").get())
                add("testImplementation", "org.junit.jupiter:junit-jupiter:5.10.2")

                // `ksp` configuration is added by the KSP Gradle plugin.
                // Because the convention plugin runs after the `plugins {}` block, the
                // `ksp` configuration already exists.
                add("ksp", "io.github.aeshen:kotlinrestify-processor:0.1.0")
            }

            // Optional: expose the generated package name as a compiler argument
            // (allows downstream projects to override it if they wish)
            extensions.configure<com.google.devtools.ksp.gradle.KspExtension> {
                arg("restify.generatedPackage", "io.github.aeshen.restify.generated")
            }

            val javaVersionTarget = JvmTarget.fromTarget(findVersion(libs, "java"))
            val kotlinLangVersionEnum = KotlinVersion.fromVersion(findVersion(libs, "kotlinLang"))

            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions {
                    jvmTarget.set(javaVersionTarget)
                    languageVersion.set(kotlinLangVersionEnum)
                    apiVersion.set(kotlinLangVersionEnum)

                    allWarningsAsErrors.set(true)

                    freeCompilerArgs.addAll(
                        listOf(
                            // • Progressive mode – enables newer language features early.
                            //   (Deprecated after Kotlin 1.8, but still works for older releases.)
                            "-Xprogressive",
                            "-Xjsr305=strict",

                            // • Enable explicit API mode – forces public APIs to be declared
                            //   with explicit visibility/modality/return types.
                            // "-Xexplicit-api=strict"

                            // • Opt‑in to experimental APIs (e.g., coroutines preview)
                            // "-Xopt-in=kotlin.RequiresOptIn"

                            // • Enable IR backend (usually default for Kotlin 1.5+)
                            // "-Xuse-ir"

                            // • Suppress specific warnings (use sparingly)
                            // "-Xsuppress-warnings=UNUSED_PARAMETER"

                            // • Enable inline classes (value classes warnings as errors
                            // "-Xerror-inline-classes"

                            // • Turn on the new type inference algorithm (experimental)
                            // "-Xnew-inference"

                            // • Enable the new JVM default methods handling
                            // "-Xjvm-default=all"   // or "compatibility"

                            // • Enable the Kotlin/JS IR backend (if you also compile JS)
                            // "-Xir-js"

                            // • Enable the Kotlin/Native memory model (experimental)
                            // "-Xmemory-model=experimental"
                        )
                    )
                }

                jvmToolchain {
                    languageVersion.set(JavaLanguageVersion.of(javaVersionTarget.asInt()))
                    vendor.set(JvmVendorSpec.ADOPTIUM)
                }
            }

            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
            }

            tasks.withType<KotlinCompile>().configureEach {
                incremental = true
            }
        }
    }

    private fun findVersion(libs: VersionCatalog, name: String): String = libs.findVersion(name)
        .orElseThrow { IllegalStateException("Version '$name' missing in libs.versions.toml") }
        .requiredVersion
}

private fun JvmTarget.asInt(): Int = when (this) {
    JvmTarget.JVM_1_8 -> 8
    JvmTarget.JVM_11  -> 11
    JvmTarget.JVM_17  -> 17
    JvmTarget.JVM_21  -> 21
    else -> throw IllegalArgumentException("Unsupported java version \"$this\"")
}
