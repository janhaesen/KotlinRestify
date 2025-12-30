package io.github.aeshen

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

class LocalPublishConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            // apply the publishing plugin to the target project
            pluginManager.apply("maven-publish")

            val defaultGroup = group?.toString() ?: "io.github.aeshen"
            val defaultVersion = version?.toString() ?: "0.1.0"

            // create convenient source/javadoc jars (use src/main/{kotlin,java} so we don't depend on sourceSets)
            val sourcesJar = tasks.register("sourcesJar", Jar::class.java) {
                archiveClassifier.set("sources")
                from(file("src/main/kotlin"))
                from(file("src/main/java"))
            }
            val javadocJar = tasks.register("javadocJar", Jar::class.java) {
                archiveClassifier.set("javadoc")
            }

            // Delay publication creation until after evaluation to ensure components are available.
            afterEvaluate {
                // only configure if a java component is present
                val javaComponent = components.findByName("java")
                    // nothing to publish for non-JVM modules
                    ?: return@afterEvaluate

                val publishing = extensions.findByType(PublishingExtension::class.java)
                    ?: return@afterEvaluate

                // avoid duplicating the same publication
                if (publishing.publications.findByName("maven") == null) {
                    publishing.publications.create("maven", MavenPublication::class.java).apply {
                        from(javaComponent)
                        artifact(sourcesJar.get())
                        artifact(javadocJar.get())
                        groupId = defaultGroup
                        artifactId = name
                        version = defaultVersion
                    }
                }

                // add mavenLocal as a repository for convenience (explicit file url to avoid DSL edge-cases)
                val home = System.getProperty("user.home")
                publishing.repositories.maven {
                    setUrl("file://$home/.m2/repository")
                }
            }
        }
    }
}

