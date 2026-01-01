package io.github.aeshen

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

open class DependencyRulesExtension {
    var allowedProjectDependencies: Map<String, Set<String>> = mapOf(
        ":annotations" to emptySet<String>(),
        ":core" to setOf(":annotations"),
        ":processor" to setOf(":annotations"),
        ":openapi" to setOf(":annotations", ":core"),
        ":sample" to setOf(":annotations", ":core", ":openapi")
    )
}

class DependencyRulesPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // create extension on the root project so it can be configured centrally from your convention plugin
        val root = project.rootProject
        val ext = root.extensions.findByType(DependencyRulesExtension::class.java)
            ?: root.extensions.create("dependencyRules", DependencyRulesExtension::class.java)

        // Ensure the check is registered only once
        val markerKey = "io.github.aeshen.dependencyRules.registered"
        if (!root.extensions.extraProperties.has(markerKey)) {
            root.extensions.extraProperties.set(markerKey, true)

            project.gradle.projectsEvaluated {
                val allowed = ext.allowedProjectDependencies
                val violations = mutableListOf<String>()

                root.allprojects.forEach { p ->
                    p.configurations.forEach { cfg ->
                        cfg.dependencies.withType(ProjectDependency::class.java).forEach { pd ->
                            val from = p.path
                            val to = pd.path
                            val allowedSet = allowed[from] ?: emptySet()
                            if (to !in allowedSet) {
                                violations += "Forbidden dependency: $from -> $to (configuration='${cfg.name}')"
                            }
                        }
                    }
                }

                if (violations.isNotEmpty()) {
                    throw GradleException("Project dependency rules violated:\n" + violations.joinToString("\n"))
                }
            }
        }
    }
}
