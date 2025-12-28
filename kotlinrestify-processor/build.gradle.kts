plugins {
    id("kotlin-jvm-convention")

    alias(libs.plugins.ksp)
}

dependencies {
    implementation(project(":kotlinrestify-annotations"))

    implementation(libs.ksp.api)

    implementation(libs.kotlin.poet)
    implementation(libs.kotlin.poet.ksp)
}

// Export the processor as a KSP symbol processor
ksp {
    arg("restify.generatedPackage", "io.github.aeshen.restify.generated")
}
