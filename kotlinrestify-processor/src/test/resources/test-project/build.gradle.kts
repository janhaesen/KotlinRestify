plugins {
    id("kotlin-jvm-convention")
    alias(libs.plugins.ksp)
}

repositories {
    mavenCentral()
}

dependencies {
    ksp("io.github.aeshen.restify:kotlinrestify-annotations") // resolved via includeBuild
}
