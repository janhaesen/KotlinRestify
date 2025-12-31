plugins {
    id("kotlin-jvm-convention")
    alias(libs.plugins.ksp)
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(project(":annotations"))
    implementation(project(":core"))

    // use local processor during KSP phase
    ksp("io.github.aeshen.restify:processor:0.1.0")
}
