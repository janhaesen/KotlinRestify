# Project Roadmap: “Batteries‑Included HTTP Client & OpenAPI Wrapper”

## 1️⃣ Define the Core Vision & USP
| Aspect | What we’ll deliver | Why it matters vs. existing solutions |
|--------|--------------------|----------------------------------------|
| **Zero‑Boilerplate HTTP Calls** | A single `api {}` DSL that auto‑creates suspend functions for GET/POST/PUT/DELETE with built‑in retry, timeout, logging, and coroutine support. | Ktor client needs manual request building; Retrofit needs interfaces + annotations. Our DSL removes the need for separate service interfaces. |
| **Optional OpenAPI Generation** | Annotation‑driven generation (`@Api`, `@Operation`, `@Param`) that produces a valid OpenAPI 3 spec **without** pulling in the HTTP client runtime. | Existing libs (Springdoc, Ktor‑OpenAPI) tightly couple to the server framework. Ours is completely decoupled – you can generate docs even for a mock‑only library. |
| **Modular Architecture** | Three independent Gradle modules: `core`, `annotations`, `openapi-generator`. Users can depend only on `annotations` for compile‑time checks or only on `core` for runtime calls. | Most libraries force you to bring the whole stack (e.g., Retrofit + OkHttp + Moshi). Our split lets you pick exactly what you need. |
| **Multiplatform Ready** | Implemented on JVM, JS (via Ktor client), and Native (via Ktor client + coroutines). | Few HTTP wrappers are truly multiplatform; most are JVM‑only. |
| **First‑Class Kotlin DSL** | Fluent, type‑safe builder (`api { baseUrl = "..."; json = Json { … } }`). | DSLs are more idiomatic than annotation‑only approaches used by Retrofit. |

---

## 2️⃣ Repository & Tooling Setup

| Step | Action | Details |
|------|--------|---------|
| **2.1** | Create GitHub repo `kotlin-batteries-http` (public, MIT license). | Add `README.md` with project tagline and contribution guide. |
| **2.2** | Initialise a **Gradle Kotlin DSL** multi‑module build. | Modules: `core`, `annotations`, `openapi-generator`, `sample-app`. |
| **2.3** | Enable **GitHub Actions** CI pipeline. | Jobs: `compile`, `test (JVM, JS, Native)`, `detekt lint`, `publish-snapshot`. |
| **2.4** | Publish snapshots to **GitHub Packages** for early adopters. | Configure `publishing` block with `maven-publish`. |
| **2.5** | Set up **Semantic Release** (or conventional commits) for automated versioning. | Guarantees clean release flow once stable. |

---

## 3️⃣ Module Design

### 3.1 `annotations` (compile‑time only)
* **Annotations**: `@Api`, `@Operation`, `@Path`, `@Query`, `@Header`, `@Body`.
* **KSP Processor**: Generates metadata classes (`ApiMeta_<Interface>.kt`) that describe endpoints.
* **No runtime dependencies** – only Kotlin stdlib and KSP.

### 3.2 `core` (runtime HTTP client)
* **Dependency**: Ktor client (multiplatform).
* **DSL**: `api { baseUrl = "..."; client { timeout = 30_000 } }`.
* **Generated Stubs**: At compile time, KSP creates extension functions that delegate to the DSL.
* **Features**: automatic retry, exponential backoff, circuit‑breaker, JSON (kotlinx‑serialization) handling, logging interceptor.

### 3.3 `openapi-generator`
* **Input**: Metadata classes produced by `annotations` processor.
* **Output**: `openapi.yaml/json` file.
* **CLI**: `./gradlew :openapi-generator:generateOpenApi --args="--output=build/openapi.yaml"`
* **Standalone**: Can be run without `core` on CI pipelines to validate docs.

### 3.4 `sample-app` (demo)
* Minimal Kotlin/JVM console app showing:
  ```kotlin
  @Api
  interface GithubApi {
      @Operation(summary = "List repos")
      @GET("/users/{user}/repos")
      suspend fun repos(@Path("user") user: String): List<Repo>
  }

  suspend fun main() {
      val api = api {
          baseUrl = "https://api.github.com"
      }
      println(api.repos("kotlin"))
  }
  ```
* Also contains a script to generate OpenAPI spec from the sample.

---

## 4️⃣ Development Sprint Plan

| Sprint | Goal | Deliverables |
|--------|------|--------------|
| **Sprint 0 (1 day)** | Repo scaffolding, CI, licensing. | GitHub repo, CI badge, `README` skeleton. |
| **Sprint 1 (1 week)** | Implement `annotations` module + KSP processor. | Working annotations, generated meta‑classes, unit tests. |
| **Sprint 2 (1 week)** | Build `core` DSL & runtime bridge. | API DSL, basic GET/POST, JSON serialization, tests on JVM & JS. |
| **Sprint 3 (1 week)** | Integrate retry/circuit‑breaker & logging. | Configurable policies, log output, benchmark simple calls. |
| **Sprint 4 (1 week)** | Create `openapi-generator` module. | CLI tool, generation from sample, validation against OpenAPI schema. |
| **Sprint 5 (4 days)** | Write documentation & quick‑start guide. | README with usage examples, API reference, contribution guide. |
| **Sprint 6 (3 days)** | Publish first **alpha** release (v0.1.0‑alpha). | Maven Central snapshot, announcement on Kotlin Slack, Reddit r/Kotlin, Twitter. |
| **Sprint 7 (1 week)** | Collect feedback, triage bugs, iterate. | Issue board populated, roadmap adjustments. |

---

## 5️⃣ Feedback Loop & Community Engagement

1. **Announcement Channels** – Kotlin Slack #kotlin‑dev, Reddit r/Kotlin, Hacker News, Twitter (use `#Kotlin` hashtag).
2. **Issue Templates** – Provide `Bug Report`, `Feature Request`, and `Documentation` templates.
3. **Discord/Slack Bot** – Auto‑post new releases and CI status.
4. **Survey** – After 2 weeks of alpha, send a short Google Form asking about ease‑of‑use, missing features, and performance.
5. **Roadmap Transparency** – Keep a `ROADMAP.md` updated with community‑voted priorities.

---

## 6️⃣ Success Metrics (to keep USP in focus)

| Metric | Target (first 3 months) |
|--------|--------------------------|
| **Adoption** | ≥ 30 GitHub stars, ≥ 5 external projects depending on the library. |
| **Documentation** | > 80 % of users report “clear enough to start”. |
| **Performance** | < 50 ms overhead vs. raw Ktor client for simple GET (measured in CI). |
| **OpenAPI Accuracy** | 100 % schema validation against generated spec (CI check). |
| **Modularity Usage** | ≥ 40 % of issues reference using only `annotations` without pulling `core`. |

---

## 7️⃣ Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| **KSP compatibility breaks** across Kotlin versions. | Build failures, delayed releases. | Pin Kotlin version for the first 6 months, add CI matrix for next minor versions. |
| **Too many features dilute USP** (becoming “just another wrapper”). | Loss of differentiation. | Stick to the three core promises: zero‑boilerplate calls, optional OpenAPI generation, modular split. |
| **Multiplatform support lagging** (Native client issues). | Limited audience. | Prioritise JVM & JS first; add Native in Sprint 4 as a separate sub‑module, flag as “experimental”. |
| **Community adoption slow** | Low feedback, stagnation. | Early outreach, demo video, blog post on Kotlin Blog, invite known Kotlin contributors as beta testers. |

---

## 8️⃣ Quick Start Snippet (to be placed in README)

```kotlin
// 1. Declare the API with annotations (no runtime dep)
@Api
interface TodoApi {
    @GET("/todos")
    suspend fun list(): List

    @POST("/todos")
    suspend fun create(@Body todo: Todo): Todo
}

// 2. Build the client (runtime)
val client = api {
    baseUrl = "https://example.com/api"
    json = Json { ignoreUnknownKeys = true }
}

// 3. Call it!
runBlocking {
    val todos = client.list()
    println(todos)
}

// 4. Generate OpenAPI spec (CLI)
./gradlew :openapi-generator:generateOpenApi --args="--output=build/openapi.yaml"
```
