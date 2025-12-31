package io.github.aeshen

import dev.detekt.gradle.Detekt
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
import org.gradle.language.base.plugins.LifecycleBasePlugin
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
            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            // Delegate to smaller helpers to keep this method concise.
            applyPluginsAndRepos(this)
            configureDependencies(this, libs)
            configureKspArgs(this)
            configureKotlinExtension(this, libs)
            configureTaskTypes(this)
            registerAggregatorAndConvenienceTasks(this)
        }
    }

    private fun applyPluginsAndRepos(project: Project) = with(project) {
        pluginManager.apply(KotlinPluginWrapper::class.java)
        pluginManager.apply(KtlintConventionPlugin::class.java)
        pluginManager.apply(DetektConventionPlugin::class.java)
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        pluginManager.apply("com.google.devtools.ksp")

        repositories {
            mavenLocal()
            mavenCentral()
        }
    }

    private fun configureDependencies(project: Project, libs: VersionCatalog) = with(project) {
        dependencies {
            // Kotlin stdlib (JVM)
            add("implementation", libs.findLibrary("kotlin.stdlib").get())

            // Serialization (JSON)
            add("implementation", libs.findLibrary("kotlinx.serialization").get())
            add("implementation", libs.findLibrary("kotlinx.serialization.json").get())

            // Coroutines
            add("implementation", libs.findLibrary("kotlinx.coroutines").get())

            // Test libraries (JUnit 5 + Kotlin test)
            add("testImplementation", libs.findLibrary("kotlin.test").get())
            add("testImplementation", libs.findLibrary("junit5-jupiter").get())
            add("testImplementation", libs.findLibrary("mockk").get())
        }
    }

    private fun configureKspArgs(project: Project) = with(project) {
        extensions.configure<com.google.devtools.ksp.gradle.KspExtension> {
            arg("restify.generatedPackage", "io.github.aeshen.restify.generated")
        }
    }

    private fun configureKotlinExtension(project: Project, libs: VersionCatalog) = with(project) {
        val javaVersionTarget = JvmTarget.fromTarget(findVersion(libs, "java"))
        val kotlinLangVersionEnum = KotlinVersion.fromVersion(findVersion(libs, "kotlinLang"))

        group = "io.github.aeshen.restify"
        version = "0.1.0"

        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget.set(javaVersionTarget)
                languageVersion.set(kotlinLangVersionEnum)
                apiVersion.set(kotlinLangVersionEnum)

                allWarningsAsErrors.set(false)

                freeCompilerArgs.addAll(
                    listOf(
                        "-progressive",
                        "-Xjsr305=strict"
                    )
                )
            }

            jvmToolchain {
                languageVersion.set(JavaLanguageVersion.of(javaVersionTarget.asInt()))
                vendor.set(JvmVendorSpec.ADOPTIUM)
            }
        }
    }

    private fun configureTaskTypes(project: Project) = with(project) {
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }

        tasks.withType<KotlinCompile>().configureEach {
            incremental = true
        }
    }

    private fun registerAggregatorAndConvenienceTasks(project: Project) = with(project) {
        // Always ensure the root project has the aggregator tasks. Guard with findByName to avoid
        // registering the same task multiple times if the plugin is applied in multiple projects.
        rootProject.run {
            if (tasks.findByName("verifyAll") == null) {
                tasks.register("verifyAll") {
                    group = LifecycleBasePlugin.VERIFICATION_GROUP
                    description = "Run ktlint and detekt checks across all projects that apply the Kotlin JVM convention."

                    dependsOn(subprojects.map { p -> p.tasks.matching { it.name == "verifyAll" } })
                    dependsOn(tasks.matching { it.name == "ktlintCheck" })
                    dependsOn(tasks.withType<Detekt>())
                }
            }

            if (tasks.findByName("ktlintFormatAll") == null) {
                tasks.register("ktlintFormatAll") {
                    group = LifecycleBasePlugin.VERIFICATION_GROUP
                    description = "Run ktlintFormat across all projects that apply the Kotlin JVM convention."

                    dependsOn(subprojects.map { p -> p.tasks.matching { it.name == "ktlintFormat" } })
                    dependsOn(tasks.matching { it.name == "ktlintFormat" })
                }
            }
        }

        // Per-project convenience task for non-root projects
        if (project != rootProject) {
            tasks.register("verifyAll") {
                group = LifecycleBasePlugin.VERIFICATION_GROUP
                description = "Run ktlint and detekt checks for this project."
                dependsOn(tasks.named("ktlintCheck"))
                dependsOn(tasks.withType<Detekt>())
            }
        }
    }

    private fun findVersion(libs: VersionCatalog, name: String): String = libs.findVersion(name)
        .orElseThrow { IllegalStateException("Version '$name' missing in libs.versions.toml") }
        .requiredVersion

    private fun JvmTarget.asInt(): Int = when (this) {
        JvmTarget.JVM_1_8 -> 8
        JvmTarget.JVM_11  -> 11
        JvmTarget.JVM_17  -> 17
        JvmTarget.JVM_21  -> 21
        else -> throw IllegalArgumentException("Unsupported java version \"$this\"")
    }
}
