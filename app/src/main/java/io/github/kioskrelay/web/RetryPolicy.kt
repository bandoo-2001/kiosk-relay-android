package io.github.kioskrelay.web

/**
 * Maps a one-based failure number to the delay before the corresponding retry.
 *
 * Returning `null` means that all retry attempts are exhausted. With the default sequence, six
 * retries are made and the seventh consecutive failure becomes fatal.
 */
class RetryPolicy(
    delaysMillis: List<Long> = DEFAULT_DELAYS_MILLIS,
) {
    private val retryDelaysMillis = delaysMillis.toList()

    init {
        require(retryDelaysMillis.isNotEmpty()) { "At least one retry delay is required" }
        require(retryDelaysMillis.all { it > 0 }) { "Retry delays must be positive" }
    }

    val maxRetries: Int
        get() = retryDelaysMillis.size

    fun delayMillisForFailure(failureNumber: Int): Long? {
        require(failureNumber > 0) { "Failure number is one-based" }
        return retryDelaysMillis.getOrNull(failureNumber - 1)
    }

    override fun equals(other: Any?): Boolean =
        other is RetryPolicy && retryDelaysMillis == other.retryDelaysMillis

    override fun hashCode(): Int = retryDelaysMillis.hashCode()

    override fun toString(): String = "RetryPolicy(delaysMillis=$retryDelaysMillis)"

    companion object {
        val DEFAULT_DELAYS_MILLIS: List<Long> =
            listOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L, 60_000L)

        fun fromSeconds(seconds: List<Int>): RetryPolicy =
            RetryPolicy(seconds.map { Math.multiplyExact(it.toLong(), 1_000L) })
    }
}
