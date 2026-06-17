package com.retro99.loops.sdk

import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger

/**
 * Controls request/response logging. Off by default — logging request bodies can leak the
 * API key (in the `Authorization` header) and contact PII into logs, so it must be opted in.
 *
 * Use [none] to disable (the default), or [of] to enable at a chosen [LogLevel] with an
 * optional custom [Logger]. When no logger is supplied, Ktor's default platform logger is
 * used (which on the JVM bridges to SLF4J when an SLF4J backend is on the classpath).
 *
 * @property level How much to log. [LogLevel.NONE] disables logging.
 * @property logger Where log lines go; `null` uses Ktor's default platform logger.
 */
class LoggingConfig private constructor(
    internal val level: LogLevel,
    internal val logger: Logger?,
) {
    companion object {
        /** Logging disabled (default). */
        fun none(): LoggingConfig = LoggingConfig(LogLevel.NONE, null)

        /**
         * Enables logging at [level], optionally routing lines to a custom [logger].
         *
         * Be aware that [LogLevel.HEADERS] and above will record the `Authorization`
         * header and request/response bodies, which contain secrets and PII.
         */
        fun of(level: LogLevel, logger: Logger? = null): LoggingConfig =
            LoggingConfig(level, logger)

        /** The default: disabled. */
        val DEFAULT: LoggingConfig = none()
    }
}
