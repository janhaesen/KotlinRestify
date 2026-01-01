plugins {
    id("kotlin-jvm-convention")
    id("local-publish-convention")
}

dependencies {
    implementation(project(":annotations"))

    // Libraries
    implementation(libs.kotlin.reflect)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.serialization)

    implementation(libs.kotlinx.serialization)
}
