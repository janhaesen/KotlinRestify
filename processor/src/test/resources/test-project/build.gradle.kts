plugins {
    id("kotlin-jvm-convention")
    alias(libs.plugins.ksp)
}

repositories {
    mavenCentral()
}

dependencies {
    ksp("io.github.aeshen.restify:annotations") // resolved via includeBuild
}
