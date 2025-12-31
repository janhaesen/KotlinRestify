plugins {
    id("kotlin-jvm-convention")
    id("ktlint-convention")

    alias(libs.plugins.ksp)
    id("local-publish-convention")
}

dependencies {
    implementation(project(":annotations"))

    implementation(libs.ksp.api)

    implementation(libs.kotlin.poet)
    implementation(libs.kotlin.poet.ksp)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(gradleTestKit())
}

// Export the processor as a KSP symbol processor
ksp {
    arg("ksp.incremental", "true")
    arg("restify.generatedPackage", "io.github.aeshen.restify.generated")
}
