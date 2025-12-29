package io.github.aeshen

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.plugin.DetektPlugin
import dev.detekt.gradle.report.ReportMergeTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin

class DetektConventionPlugin: Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val libs = extensions.getByType<VersionCatalogsExtension>()
                .named("libs")

            pluginManager.apply(DetektPlugin::class.java)

            extensions.configure<DetektExtension>("detekt") {
                toolVersion.set(libs.findVersion("detekt").get().toString())
                buildUponDefaultConfig.set(true)
                ignoreFailures.set(false)
                parallel.set(true)
                config.setFrom(rootProject.file("detekt.yml"))
            }

            val reportMerge = tasks.register<ReportMergeTask>("reportMerge") {
                group = LifecycleBasePlugin.VERIFICATION_GROUP
                output.set(rootProject.layout.buildDirectory.file("reports/detekt/detekt.sarif"))
            }

            reportMerge.configure {
                input.from(tasks.withType<Detekt>().map { it.reports.sarif.required.set(true) })
            }
        }
    }
}
