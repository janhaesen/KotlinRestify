package io.github.aeshen.restify.runtime

import io.github.aeshen.restify.runtime.client.AdapterHttpClient
import io.github.aeshen.restify.runtime.client.DefaultApiCaller
import io.github.aeshen.restify.runtime.client.adapter.HttpClientAdapter
import io.github.aeshen.restify.runtime.client.adapter.ktor.KtorHttpClientAdapter
import io.github.aeshen.restify.runtime.client.body.DefaultResponseMapperFactory
import io.github.aeshen.restify.runtime.client.body.ResponseMapperFactory
import io.github.aeshen.restify.runtime.client.path.UrlBuilder
import io.github.aeshen.restify.runtime.client.path.impl.DefaultUrlBuilder
import io.github.aeshen.restify.runtime.retry.RetryPolicy
import io.github.aeshen.restify.runtime.retry.TimeBoundRetryPolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.Closeable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

object ApiClientFactory {
    fun builder(): Builder = Builder()

    class Builder internal constructor() {
        private var adapter: HttpClientAdapter? = null
        private var defaultRetryTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
        private var configureCfg: ApiConfig.() -> Unit = {}
        private var explicitRetryPolicy: RetryPolicy? = null
        private var perClientCallerMode: Boolean = false

        fun adapter(adapter: HttpClientAdapter?) = apply { this.adapter = adapter }

        fun defaultRetryTimeoutMillis(ms: Long) = apply { this.defaultRetryTimeoutMillis = ms }

        fun configure(configure: ApiConfig.() -> Unit) = apply { this.configureCfg = configure }

        fun retryPolicy(policy: RetryPolicy?) =
            apply {
                this.explicitRetryPolicy =
                    policy
            }

        fun perClientCallerMode(enabled: Boolean) = apply { this.perClientCallerMode = enabled }

        /**
         * Build an ApiCaller wired with the configured adapter and ApiConfig.
         * Returns a managed caller which can be closed to release underlying resources.
         */
        fun buildCaller(): ApiCaller = buildManagedCaller()

        /**
         * Build and return a ManagedApiCaller that implements ApiCaller + Closeable.
         * Closing it will release the underlying low-level adapter once.
         */
        fun buildManagedCaller(): ManagedApiCaller {
            // Build immutable ApiConfig and ensure a retry policy is present
            val baseCfg =
                ApiConfig(baseUrl = "")
                    .apply(configureCfg)
            val resolvedRetry =
                baseCfg.retryPolicy ?: explicitRetryPolicy
                    ?: TimeBoundRetryPolicy(
                        defaultRetryTimeoutMillis,
                    )
            val cfg = baseCfg.copy(retryPolicy = resolvedRetry)

            val effectiveAdapter = adapter ?: KtorHttpClientAdapter()
            val adapterHttpClient =
                AdapterHttpClient(
                    effectiveAdapter,
                    cfg,
                )
            val caller =
                DefaultApiCaller(
                    adapterHttpClient,
                )

            // Do not expose AdapterHttpClient type in the ManagedApiCaller constructor.
            return ManagedApiCaller(
                delegate = caller,
                closeAdapter = { adapterHttpClient.closeAdapter() },
                apiConfigSupplier = { adapterHttpClient.baseConfig },
            )
        }

        /**
         * Instantiate a generated client class via reflection. The constructor must accept
         * ApiCaller + ApiConfig (optional additional parameters supported as before).
         *
         * If `perClientCallerMode` is enabled the factory will create a dedicated caller for each
         * generated client (isolated lifecycle/config).
         */
        @Suppress("UNCHECKED_CAST")
        fun <T : Any> createClient(clientClass: KClass<T>): T {
            val callerForCtor: ApiCaller
            val cfgForCtor: ApiConfig

            // Always build a managed caller (shared or per-client behavior unchanged)
            val managed = buildManagedCaller()
            callerForCtor = managed
            cfgForCtor = managed.toApiConfig()

            val mapperFactory = DefaultResponseMapperFactory()
            val dispatcher: CoroutineDispatcher = Dispatchers.IO

            val ctor = getConstructor(clientClass)

            // map parameters
            val args =
                mapParameters(
                    ctor = ctor,
                    callerForCtor = callerForCtor,
                    cfgForCtor = cfgForCtor,
                    mapperFactory = mapperFactory,
                    dispatcher = dispatcher,
                    clientClass = clientClass,
                )

            return ctor.callBy(args)
        }

        private fun <T : Any> mapParameters(
            ctor: KFunction<T>,
            callerForCtor: ManagedApiCaller,
            cfgForCtor: ApiConfig,
            mapperFactory: DefaultResponseMapperFactory,
            dispatcher: CoroutineDispatcher,
            clientClass: KClass<T>,
        ): MutableMap<KParameter, Any?> {
            val args = mutableMapOf<KParameter, Any?>()
            ctor.parameters.forEach { p ->
                val cls = p.type.classifier as? KClass<*>
                when (cls) {
                    ApiCaller::class -> {
                        args[p] = callerForCtor
                    }

                    ApiConfig::class -> {
                        args[p] = cfgForCtor
                    }

                    UrlBuilder::class -> {
                        args[p] = DefaultUrlBuilder
                    }

                    ResponseMapperFactory::class -> {
                        args[p] = mapperFactory
                    }

                    CoroutineDispatcher::class -> {
                        args[p] = dispatcher
                    }

                    else -> {
                        if (p.isOptional || p.type.isMarkedNullable) {
                            // rely on default or null
                        } else {
                            throw IllegalArgumentException(
                                "Constructor parameter ${p.name} of ${clientClass.qualifiedName}" +
                                    " is unsupported by createClient",
                            )
                        }
                    }
                }
            }
            return args
        }

        /* Try to find a constructor that matches either:
         *  - (ApiCaller, ApiConfig, ...)
         *  - (ApiCaller, ResponseMapperFactory, [CoroutineDispatcher], ...)
         */
        private fun <T : Any> getConstructor(clientClass: KClass<T>): KFunction<T> =
            clientClass.constructors
                .firstOrNull { cons ->
                    val paramTypes =
                        cons.parameters.mapNotNull {
                            it.type.classifier as? KClass<*>
                        }
                    // must contain ApiCaller
                    if (ApiCaller::class !in paramTypes) {
                        return@firstOrNull false
                    }

                    // accept if it contains ApiConfig (old path)
                    if (ApiConfig::class in paramTypes) {
                        return@firstOrNull true
                    }

                    // or accept if it contains ResponseMapperFactory (generated client path)
                    if (ResponseMapperFactory::class in paramTypes) {
                        return@firstOrNull true
                    }

                    false
                }
                ?: throw IllegalArgumentException(
                    "No suitable constructor found on ${clientClass.qualifiedName}. " +
                        "Expected constructor accepting ApiCaller + (ApiConfig | ResponseMapperFactory).",
                )

        inline fun <reified T : Any> createClient(): T = createClient(T::class)

        fun api(): ApiCaller = buildCaller()
    }

    /**
     * ManagedApiCaller: wrapper that forwards calls and exposes `close()` to release resources.
     *
     * Public API avoids exposing internal AdapterHttpClient by accepting only public types
     * (ApiCaller) and two function values for closing and retrieving ApiConfig.
     */
    class ManagedApiCaller(
        private val delegate: ApiCaller,
        private val closeAdapter: () -> Unit,
        private val apiConfigSupplier: () -> ApiConfig,
    ) : ApiCaller,
        Closeable {
        override suspend fun <T> call(
            request: RequestData,
            mapper: io.github.aeshen.restify.runtime.client.body.ResponseMapper<T>,
        ): T = delegate.call(request, mapper)

        override fun close() {
            closeAdapter()
        }

        /**
         * Helper to reconstruct the ApiConfig used by the managed transport (useful for passing into generated client constructors).
         */
        fun toApiConfig(): ApiConfig = apiConfigSupplier()
    }
}

fun <T : Any> ApiClientFactory.createClient(
    clientClass: KClass<T>,
    adapter: HttpClientAdapter? = null,
    defaultRetryTimeoutMillis: Long = 10_000L,
    configure: ApiConfig.() -> Unit = {},
): T =
    ApiClientFactory
        .builder()
        .adapter(adapter)
        .defaultRetryTimeoutMillis(defaultRetryTimeoutMillis)
        .configure(configure)
        .createClient(clientClass)

inline fun <reified T : Any> ApiClientFactory.createClient(
    adapter: HttpClientAdapter? = null,
    defaultRetryTimeoutMillis: Long = 10_000L,
    noinline configure: ApiConfig.() -> Unit = {},
): T = createClient(T::class, adapter, defaultRetryTimeoutMillis, configure)
