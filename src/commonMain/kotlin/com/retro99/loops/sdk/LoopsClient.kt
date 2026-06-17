package com.retro99.loops.sdk

import com.retro99.loops.sdk.api.CampaignsApi
import com.retro99.loops.sdk.api.ComponentsApi
import com.retro99.loops.sdk.api.ContactPropertiesApi
import com.retro99.loops.sdk.api.ContactsApi
import com.retro99.loops.sdk.api.DedicatedSendingIpsApi
import com.retro99.loops.sdk.api.EmailMessagesApi
import com.retro99.loops.sdk.api.EventsApi
import com.retro99.loops.sdk.api.MailingListsApi
import com.retro99.loops.sdk.api.ThemesApi
import com.retro99.loops.sdk.api.TransactionalApi
import com.retro99.loops.sdk.api.UploadsApi
import com.retro99.loops.sdk.ksp.JvmAsync
import com.retro99.loops.sdk.model.ApiKeyResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get

/**
 * Kotlin Multiplatform client for the loops.so API (https://loops.so/docs/api-reference).
 *
 * **Server-side / trusted use** — holds the real Loops API key, talks to Loops directly:
 * ```kotlin
 * val client = LoopsClient.direct(apiKey = "YOUR_API_KEY")
 * ```
 *
 * **Mobile / untrusted use** — points at your own backend proxy; the proxy holds the key:
 * ```kotlin
 * val client = LoopsClient.proxy(
 *     proxyUrl = "https://your-backend.com/loops/",
 *     auth = ProxyAuth.BearerToken { session.token() },
 * )
 * ```
 *
 * The client owns an [HttpClient]; call [close] when done.
 */
@JvmAsync
class LoopsClient private constructor(
    private val config: LoopsConfig,
    engine: HttpClientEngine,
) {
    // region Constructors
    constructor(apiKey: String) :
        this(LoopsConfig.Direct(apiKey, LOOPS_BASE_URL), httpClientEngine())

    constructor(apiKey: String, baseUrl: String) :
        this(LoopsConfig.Direct(apiKey, baseUrl), httpClientEngine())

    constructor(proxyUrl: String, auth: ProxyAuth) :
        this(LoopsConfig.Proxy(proxyUrl, auth), httpClientEngine())
    // endregion

    private val client: HttpClient = loopsHttpClient(config, engine)

    // Internal (not private) so the KSP-generated `LoopsClientAsync` wrappers, which live in
    // this package as top-level extensions, can reach `http.asyncScope`.
    internal val http = LoopsHttp(client, config.retry)

    // region Resources
    /** Contacts resource group. */
    val contacts: ContactsApi = ContactsApi(http)

    /** Contact Properties resource group. */
    val contactProperties: ContactPropertiesApi = ContactPropertiesApi(http)

    /** Events resource group. */
    val events: EventsApi = EventsApi(http)

    /** Transactional Emails resource group. */
    val transactional: TransactionalApi = TransactionalApi(http)

    /** Mailing Lists resource group. */
    val lists: MailingListsApi = MailingListsApi(http)

    /** Campaigns resource group. */
    val campaigns: CampaignsApi = CampaignsApi(http)

    /** Email Messages resource group. */
    val emailMessages: EmailMessagesApi = EmailMessagesApi(http)

    /** Themes resource group. */
    val themes: ThemesApi = ThemesApi(http)

    /** Components resource group. */
    val components: ComponentsApi = ComponentsApi(http)

    /** Dedicated Sending IPs resource group. */
    val sendingIps: DedicatedSendingIpsApi = DedicatedSendingIpsApi(http)

    /** Uploads resource group (image assets for email content). */
    val uploads: UploadsApi = UploadsApi(http)

    /**
     * Tests the API key (GET /api-key). Returns the team the key belongs to,
     * or throws [LoopsException] if the key is invalid.
     */
    suspend fun testApiKey(): ApiKeyResponse =
        http.execute { get("api-key").body() }
    // endregion

    fun close() {
        http.close()
    }

    companion object {
        /** The Loops production API base URL. Pass as [direct]'s `baseUrl` only for staging / EU residency overrides. */
        const val LOOPS_BASE_URL: String = "https://app.loops.so/api/v1/"

        // region Direct (trusted) factories
        /**
         * Creates a client for **server-side / trusted** use. Holds the Loops API key and
         * talks to Loops directly. Never use this in a mobile app — the key would be
         * extractable from the binary.
         *
         * @param apiKey Your Loops account API key.
         * @param baseUrl Override only for Loops staging or EU data-residency endpoints.
         * @param retry How HTTP 429 responses are retried. Defaults to [RetryConfig.DEFAULT];
         *   pass [RetryConfig.NONE] to disable retrying.
         */
        fun direct(apiKey: String): LoopsClient = direct(apiKey, LOOPS_BASE_URL)

        fun direct(apiKey: String, baseUrl: String): LoopsClient =
            direct(apiKey, baseUrl, RetryConfig.DEFAULT)

        fun direct(
            apiKey: String,
            baseUrl: String,
            retry: RetryConfig,
        ): LoopsClient = direct(apiKey, baseUrl, retry, httpClientEngine())

        fun direct(
            apiKey: String,
            baseUrl: String,
            engine: HttpClientEngine,
        ): LoopsClient = direct(apiKey, baseUrl, RetryConfig.DEFAULT, engine)

        fun direct(
            apiKey: String,
            baseUrl: String,
            retry: RetryConfig,
            engine: HttpClientEngine,
        ): LoopsClient = LoopsClient(LoopsConfig.Direct(apiKey, baseUrl, retry), engine)

        /**
         * Full-control [direct] factory exposing network [timeout] and request [logging]
         * alongside [retry]. Use this overload when you need to tune timeouts or enable
         * logging; the shorter overloads cover the common cases.
         *
         * Provided as a distinct overload (rather than a defaulted `engine` parameter) so
         * Swift/Objective-C callers — which do not see Kotlin default arguments — can call
         * it without supplying an [HttpClientEngine].
         */
        fun direct(
            apiKey: String,
            baseUrl: String,
            retry: RetryConfig,
            timeout: TimeoutConfig,
            logging: LoggingConfig,
        ): LoopsClient = direct(apiKey, baseUrl, retry, timeout, logging, httpClientEngine())

        fun direct(
            apiKey: String,
            baseUrl: String,
            retry: RetryConfig,
            timeout: TimeoutConfig,
            logging: LoggingConfig,
            engine: HttpClientEngine,
        ): LoopsClient = LoopsClient(
            LoopsConfig.Direct(apiKey, baseUrl, retry, timeout, logging),
            engine,
        )
        // endregion

        // region Proxy (untrusted) factories
        /**
         * Creates a client for **mobile / untrusted** use. Points at your own backend proxy
         * which holds the real Loops API key server-side. The Loops key is never present here.
         *
         * @param proxyUrl Base URL of your backend proxy (e.g. `"https://your-api.com/loops/"`).
         * @param auth How to authenticate the app to your proxy. The single-argument
         *   overload uses [ProxyAuth.None].
         * @param retry How HTTP 429 responses are retried. Defaults to [RetryConfig.DEFAULT];
         *   pass [RetryConfig.NONE] to disable retrying.
         */
        fun proxy(proxyUrl: String): LoopsClient = proxy(proxyUrl, ProxyAuth.None)

        fun proxy(proxyUrl: String, auth: ProxyAuth): LoopsClient =
            proxy(proxyUrl, auth, RetryConfig.DEFAULT)

        fun proxy(
            proxyUrl: String,
            auth: ProxyAuth,
            retry: RetryConfig,
        ): LoopsClient = proxy(proxyUrl, auth, retry, httpClientEngine())

        fun proxy(
            proxyUrl: String,
            auth: ProxyAuth,
            engine: HttpClientEngine,
        ): LoopsClient = proxy(proxyUrl, auth, RetryConfig.DEFAULT, engine)

        fun proxy(
            proxyUrl: String,
            auth: ProxyAuth,
            retry: RetryConfig,
            engine: HttpClientEngine,
        ): LoopsClient = LoopsClient(LoopsConfig.Proxy(proxyUrl, auth, retry), engine)

        /**
         * Full-control [proxy] factory exposing network [timeout] and request [logging]
         * alongside [retry]. Use this overload when you need to tune timeouts or enable
         * logging; the shorter overloads cover the common cases.
         *
         * Provided as a distinct overload (rather than a defaulted `engine` parameter) so
         * Swift/Objective-C callers — which do not see Kotlin default arguments — can call
         * it without supplying an [HttpClientEngine].
         */
        fun proxy(
            proxyUrl: String,
            auth: ProxyAuth,
            retry: RetryConfig,
            timeout: TimeoutConfig,
            logging: LoggingConfig,
        ): LoopsClient = proxy(proxyUrl, auth, retry, timeout, logging, httpClientEngine())

        fun proxy(
            proxyUrl: String,
            auth: ProxyAuth,
            retry: RetryConfig,
            timeout: TimeoutConfig,
            logging: LoggingConfig,
            engine: HttpClientEngine,
        ): LoopsClient = LoopsClient(
            LoopsConfig.Proxy(proxyUrl, auth, retry, timeout, logging),
            engine,
        )
        // endregion
    }
}
