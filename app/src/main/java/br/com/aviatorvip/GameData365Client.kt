package br.com.aviatorvip

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente mínimo da GameData365 para o AirJet.
 *
 * Execute as chamadas fora da thread principal. Não grave a chave no código
 * nem em arquivos versionados; solicite-a ao usuário e guarde-a localmente.
 */
class GameData365Client(
    private val apiKey: String,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 10_000
) {
    data class Round(
        val roundId: String?,
        val multiplier: Double,
        val timestamp: String?
    )

    data class History(
        val rounds: List<Round>,
        val nextCursor: String?
    )

    init {
        require(apiKey.isNotBlank()) { "A chave da GameData365 é obrigatória." }
    }

    fun fetchLatestAirJet(): Round {
        val json = get("/v1/crash/live")
        val latest = json.optJSONObject("latest")
            ?: throw IllegalStateException("A API não retornou o campo latest.")
        val gameId = json.optString("game_id")
        require(gameId == "air_jet") {
            "A API retornou outro jogo: ${gameId.ifBlank { "desconhecido" }}."
        }
        val multiplier = latest.optDouble("multiplier", Double.NaN)
        require(multiplier.isFinite() && multiplier >= 1.0) {
            "Multiplicador ausente ou inválido na resposta."
        }
        return Round(
            roundId = latest.optString("round_id").takeIf { it.isNotBlank() },
            multiplier = multiplier,
            timestamp = latest.optString("timestamp").takeIf { it.isNotBlank() }
        )
    }

    fun fetchAirJetHistory(limit: Int = 50, cursor: String? = null): History {
        require(limit in 1..50) { "O limite deve ficar entre 1 e 50." }
        val query = buildString {
            append("?game=air_jet&limit=").append(limit)
            if (!cursor.isNullOrBlank()) append("&cursor=").append(
                java.net.URLEncoder.encode(cursor, Charsets.UTF_8.name())
            )
        }
        val json = get("/v1/crash/history$query")
        val array = json.optJSONArray("rounds")
            ?: throw IllegalStateException("A API não retornou a lista rounds.")
        val rounds = buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val value = item.optDouble("multiplier", Double.NaN)
                if (value.isFinite() && value >= 1.0) {
                    add(
                        Round(
                            roundId = item.optString("round_id").takeIf { it.isNotBlank() },
                            multiplier = value,
                            timestamp = item.optString("timestamp").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }
        return History(rounds, json.optString("next_cursor").takeIf { it.isNotBlank() })
    }

    private fun get(pathAndQuery: String): JSONObject {
        val connection = (URL(BASE_URL + pathAndQuery).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("X-API-Key", apiKey)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("GameData365 respondeu HTTP $code: ${body.take(300)}")
            }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BASE_URL = "https://api.gamedata365.com"
    }
}
