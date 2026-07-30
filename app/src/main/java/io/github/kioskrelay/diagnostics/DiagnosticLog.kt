package io.github.kioskrelay.diagnostics

import io.github.kioskrelay.config.UrlPolicy
import java.util.ArrayDeque
import java.util.Locale

enum class DiagnosticLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
}

data class DiagnosticEvent(
    val timestampEpochMillis: Long,
    val level: DiagnosticLevel,
    val category: String,
    val message: String,
)

interface DiagnosticLog {
    fun record(
        category: String,
        message: String,
        level: DiagnosticLevel = DiagnosticLevel.INFO,
    )

    fun snapshot(): List<DiagnosticEvent>

    fun clear()

    fun exportText(): String
}

class DiagnosticRingLog(
    capacity: Int = DEFAULT_CAPACITY,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : DiagnosticLog {
    private val maximumEntries = capacity.coerceIn(1, MAX_CAPACITY)
    private val events = ArrayDeque<DiagnosticEvent>(maximumEntries)
    private val lock = Any()

    override fun record(
        category: String,
        message: String,
        level: DiagnosticLevel,
    ) {
        val event = DiagnosticEvent(
            timestampEpochMillis = nowEpochMillis(),
            level = level,
            category = DiagnosticRedactor.redact(category).take(MAX_CATEGORY_LENGTH),
            message = DiagnosticRedactor.redact(message).take(MAX_MESSAGE_LENGTH),
        )
        synchronized(lock) {
            while (events.size >= maximumEntries) events.removeFirst()
            events.addLast(event)
        }
    }

    override fun snapshot(): List<DiagnosticEvent> = synchronized(lock) {
        events.toList()
    }

    override fun clear() {
        synchronized(lock) { events.clear() }
    }

    override fun exportText(): String = snapshot().joinToString(separator = "\n") { event ->
        "${event.timestampEpochMillis}\t${event.level.name}\t${event.category}\t${event.message}"
    }

    private companion object {
        const val DEFAULT_CAPACITY = 200
        const val MAX_CAPACITY = 1_000
        const val MAX_CATEGORY_LENGTH = 64
        const val MAX_MESSAGE_LENGTH = 500
    }
}

object DiagnosticRedactor {
    private val secretAssignment = Regex(
        pattern = """(?i)\b(authorization|cookie|set-cookie|password|passwd|token|secret|api[-_]?key)\s*[:=]\s*([^,;\r\n]+)""",
    )
    private val bearerToken = Regex("""(?i)\bbearer\s+[A-Za-z0-9._~+/=-]+""")
    private val jwtToken = Regex(
        """\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b""",
    )
    private val url = Regex("""(?i)\bhttps?://[^\s"'<>]+""")

    fun redact(value: String): String {
        var result = bearerToken.replace(value, "Bearer <redacted>")
        result = jwtToken.replace(result, "<redacted-jwt>")
        result = secretAssignment.replace(result) { match ->
            "${match.groupValues[1].lowercase(Locale.US)}=<redacted>"
        }
        result = url.replace(result) { match ->
            UrlPolicy.normalizeOrigin(match.value.trimEnd('.', ',', ')', ']'))
                ?: "<redacted-url>"
        }
        return result.replace('\n', ' ').replace('\r', ' ')
    }
}
