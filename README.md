# KotlinRestify

**KotlinRestify** is a lightweight, opinionated wrapper around the Ktor HTTP client that lets you declare REST endpoints with simple Kotlin annotations. From those same annotations it automatically generates a complete OpenAPI specification – no separate Swagger files, no duplicate definitions.

> **USP** – *One source of truth*: a single annotated interface gives you ready‑to‑use coroutine‑based HTTP calls **and** a ready‑to‑publish OpenAPI spec, all while staying completely modular (you can use only the annotation processor without pulling in the HTTP client at all).

---

## Table of Contents

1. [Features](#features)
2. [Getting Started](#getting-started)
    - [Add the dependencies](#add-the-dependencies)
    - [Define an API interface](#define-an-api-interface)
    - [Create a client instance](#create-a-client-instance)
    - [Generate OpenAPI spec](#generate-openapi-spec)
3. [Modules Overview](#modules-overview)
4. [Configuration Options](#configuration-options)
5. [Extending the Core](#extending-the-core)
6. [Building & Publishing](#building--publishing)
7. [Contributing](#contributing)
8. [License](#license)

---

## Features

- **Annotation‑first API definition** (`@HttpGet`, `@HttpPost`, `@Path`, `@Query`, `@Header`, …)
- **Coroutine‑friendly** request execution (`suspend fun …`)
- **Zero boiler‑plate** serialization/deserialization via `kotlinx‑serialization`
- **Built‑in cross‑cutting concerns** (optional retry, circuit‑breaker, logging, metrics)
- **Automatic OpenAPI 3.0 generation** from the same annotations (KSP processor)
- **Multiplatform** (JVM, JS, Native) – the core annotation processor has **no runtime dependency** on the HTTP client.
- **Modular architecture** – you can depend only on `kotlinrestify-annotations` if you just need the spec generator.

---

## Getting Started

### Add the dependencies

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "1.9.0"
    kotlin("kapt") version "1.9.0"   // for KSP processors
}

repositories {
    mavenCentral()
}

// Core (includes annotation processor + runtime)
implementation("dev.myorg:kotlinrestify-core:0.1.0")

// Optional modules – add only what you need
implementation("dev.myorg:kotlinrestify-retry:0.1.0")        // retry & circuit‑breaker
implementation("dev.myorg:kotlinrestify-openapi:0.1.0")    // OpenAPI generator

ksp("dev.myorg:kotlinrestify-processor:0.1.0") // annotation processing
```

> **Tip:** If you only want the OpenAPI generator, depend solely on `kotlinrestify-annotations` + `kotlinrestify-openapi`.

### Define an API interface

```kotlin
import dev.myorg.kotlinrestify.annotations.*

interface GithubApi {

    @HttpGet("/users/{username}")
    suspend fun getUser(
        @Path("username") username: String
    ): GithubUser

    @HttpPost("/repos/{owner}/{repo}/issues")
    suspend fun createIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body issue: NewIssue
    ): IssueResponse
}
```

*All parameters are automatically serialized/deserialized using `kotlinx‑serialization`.*

### Create a client instance

```kotlin
import dev.myorg.kotlinrestify.client.*

val factory =
    ApiClientFactory
        .builder()
        .config("https://api.github.com")
        .responseMapperFactory(KotlinxResponseMapperFactory(kotlinJsonProvider))
        .defaultRetryTimeoutMillis(DEFAULT_TIMEOUT_MILLIS)
        .retryPolicy(TimeBoundRetryPolicy(DEFAULT_TIMEOUT_MILLIS))

val github: GithubApi = factory.createClient<GithubApi>
```

Now you can call `github.getUser("octocat")` inside any coroutine scope.

### Generate OpenAPI spec

The OpenAPI generator runs at **compile time** via KSP. After a successful build you’ll find a file called `openapi.yaml` in the `build/generated/ksp/main/kotlin` directory.

```bash
./gradlew clean build
cat build/generated/ksp/main/kotlin/openapi.yaml
```

You can feed this file directly to Swagger UI, Redoc, or any API‑gateway that consumes OpenAPI.

---

## Modules Overview

| Module                     | Purpose                                            | Dependency on HTTP client |
|----------------------------|----------------------------------------------------|---------------------------|
| `kotlinrestify-annotations` | Annotation definitions only (no runtime code)      | **None**                  |
| `kotlinrestify-processor`   | KSP processor that builds `RequestSpec` objects    | **None**                  |
| `kotlinrestify-core`        | Runtime implementation (uses Ktor client)         | **Yes**                   |
| `kotlinrestify-openapi`     | Generates OpenAPI 3.0 spec from annotations        | **None** (compile‑time only) |

---

## Configuration Options

```kotlin
ApiClientFactory
    .builder()
    .config("https://api.github.com")
    .responseMapperFactory(KotlinxResponseMapperFactory(kotlinJsonProvider))
    .defaultRetryTimeoutMillis(DEFAULT_TIMEOUT_MILLIS)
    .retryPolicy(TimeBoundRetryPolicy(DEFAULT_TIMEOUT_MILLIS))
```

All options are **fluent** and can be omitted for sensible defaults.

---

## Extending the Core

Because the core is deliberately thin, you can plug in your own:

* **Custom authentication** – implement `AuthProvider` and pass it to the builder.
* **Alternative serialization** – replace the default `Json` instance with a custom `KSerializer`.
* **Additional interceptors** – add a `HttpSendInterceptor` to manipulate requests globally.

---

## Building & Publishing

```bash
# Run all tests on every platform
./gradlew clean testAll

# Publish a snapshot (CI does this automatically)
./gradlew publishToMavenLocal
```

The project follows **Semantic Versioning**. Tag a release with `v0.1.0` and push to GitHub; GitHub Actions will publish the artifact to Maven Central.

---

## Contributing

We welcome contributions! Please:

1. Fork the repository.
2. Create a feature branch (`git checkout -b feat/your-feature`).
3. Write tests for any new behavior.
4. Submit a Pull Request with a clear description.

See `CONTRIBUTING.md` for coding style, commit guidelines, and the review process.

---

## License

`KotlinRestify` is released under the **Apache License 2.0** – see the `LICENSE` file for details.

---

**Happy coding!** 🎉

*Feel free to open an issue if you run into trouble, have ideas for improvements, or just want to share how you’re using KotlinRestify.*
