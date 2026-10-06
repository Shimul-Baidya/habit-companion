package com.example.habit.coach

import java.security.MessageDigest

data class StrategyCard(val id: String, val title: String, val principle: String,
    val action: String, val useWhen: String, val tags: List<String>, val source: String)

/** Content and attribution are supplied prose, never executable instructions or proven claims. */
class StrategyCatalog private constructor(val cards: List<StrategyCard>, val sha256: String) {
    val byId = cards.associateBy { it.id }
    companion object {
        fun validated(cards: List<StrategyCard>, bytes: ByteArray): StrategyCatalog {
            require(cards.isNotEmpty() && cards.size <= 1000)
            require(cards.map { it.id }.distinct().size == cards.size)
            cards.forEach { c ->
                require(c.id.matches(Regex("[a-zA-Z0-9_-]{1,80}")))
                listOf(c.title, c.principle, c.action, c.useWhen, c.source).forEach {
                    require(it.isNotBlank() && it.length <= 4000 && it.none { ch -> ch.isISOControl() })
                }
                require(c.tags.isNotEmpty() && c.tags.size <= 30 && c.tags.distinct().size == c.tags.size)
                require(c.tags.all { it.isNotBlank() && it.length <= 80 && it.none(Char::isISOControl) })
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            return StrategyCatalog(cards.map { it.copy(tags = it.tags.toList()) }, digest)
        }
    }
}

sealed interface CatalogResult {
    data class Ready(val catalog: StrategyCatalog) : CatalogResult
    data class Unavailable(val failure: CoachFailure) : CatalogResult
}

/** Android asset access is injected; failure never creates a replacement library. */
class StrategyRepository(private val readAsset: () -> ByteArray) {
    suspend fun load(): CatalogResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val bytes = readAsset()
            CatalogResult.Ready(CoachJson.catalog(bytes))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (_: java.io.FileNotFoundException) { CatalogResult.Unavailable(CoachFailure.CatalogMissing)
        } catch (_: Exception) { CatalogResult.Unavailable(CoachFailure.CatalogInvalid) }
    }
}
