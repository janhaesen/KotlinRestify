package io.github.aeshen

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jlleitschuh.gradle.ktlint.KtlintExtension

class KtlintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // -------------------------------------------------
            // 1️⃣ Apply the real ktlint plugin
            // -------------------------------------------------
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")

            val libs = extensions.getByType<VersionCatalogsExtension>()
                .named("libs")

            // -------------------------------------------------
            // 2️⃣ Configure ktlint (uses the version from the catalog)
            // -------------------------------------------------
            extensions.configure<KtlintExtension>("ktlint") {
                version.set(libs.findVersion("ktlint").get().requiredVersion)
                android.set(false)
                outputToConsole.set(true)
                coloredOutput.set(true)
                ignoreFailures.set(false)

                // -----------------------------------------------------------------
                // Exclude generated sources (KSP output, build‑logic, etc.)
                // -----------------------------------------------------------------
                filter {
                    exclude("**/generated/**")
                    exclude("**/ksp/**")
                    exclude("**/build-logic/**")
                }

                // -----------------------------------------------------------------
                // Optional: point to a shared .editorconfig (if you have one)
                // -----------------------------------------------------------------
                // additionalEditorconfig.set(rootProject.file(".editorconfig"))
            }

            // -------------------------------------------------
            // 3️⃣ Hook ktlint into the normal lifecycle
            // -------------------------------------------------
            tasks.named("check") {
                dependsOn(tasks.named("ktlintCheck"))
            }
        }
    }
}
