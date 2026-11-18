package br.com.aviatorvip

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente da API GameData365 para localizar o AirJet.
 *
 * O game_id é descoberto pela resposta de /v1/crash/live; não presumimos
 * que o identificador interno do jogo seja "airjet".
 * Execute as chamadas fora da thread principal.
 */
class GameData365Client(
    private val apiKey: String,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 10_000
) {
    data class Round(
        val roundId: String,
        val multiplier: Double,
        val timestamp: String?
    )

    data class History(
        val gameId: String,
        val rounds: List<Round>,
        val nextCursor: String?
    )

    init {
        require(apiKey.isNotBlank()) { "A chave da GameData365 é obrigatória." }
    }

    /** Localiza o identificador interno de um jogo pelo nome retornado pela API. */
    fun findGameId(gameName: String): String {
        require(gameName.isNotBlank()) { "Informe o nome do jogo." }
        val json = get("/v1/crash/live")
        require(json.optString("status") == "ok") {
            "A API não confirmou status ok na consulta ao vivo."
        }
        val games = json.optJSONArray("games")
            ?: throw IllegalStateException("A API não retornou a lista games.")
        for (i in 0 until games.length()) {
            val game = games.optJSONObject(i) ?: continue
            if (game.optString("game_name").contains(gameName, ignoreCase = true)) {
                return game.optString("game_id").takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("$gameName veio sem game_id.")
            }
        }
        val disponiveis = (0 until games.length()).mapNotNull { index ->
            games.optJSONObject(index)?.let { game ->
                "${game.optString("game_name", "sem nome")} (ID: ${game.optString("game_id", "sem ID")})"
            }
        }
        throw IllegalStateException(
            if (disponiveis.isEmpty()) {
                "A API não retornou jogos liberados para esta chave."
            } else {
                "$gameName não foi encontrado. Jogos retornados pela API: " +
                    disponiveis.joinToString("; ")
            }
        )
    }

    fun findAirJetGameId(): String = findGameId("AirJet")

    /** Retorna a última rodada do jogo cujo nome contém AirJet. */
    fun fetchLatestAirJet(): Round {
        val json = get("/v1/crash/live")
        require(json.optString("status") == "ok") {
            "A API não confirmou status ok na consulta ao vivo."
        }
        val games = json.optJSONArray("games")
            ?: throw IllegalStateException("A API não retornou a lista games.")
        for (i in 0 until games.length()) {
            val game = games.optJSONObject(i) ?: continue
            if (!game.optString("game_name").contains("AirJet", ignoreCase = true)) continue
            val latest = game.optJSONObject("latest")
                ?: throw IllegalStateException("O AirJet não trouxe latest.")
            return parseRound(latest)
        }
        val disponiveis = (0 until games.length()).mapNotNull { i ->
            games.optJSONObject(i)?.let { game ->
                "${game.optString("game_name")} [${game.optString("game_id")}]"
            }
        }
        throw IllegalStateException(
            "AirJet não veio na lista desta chave. Jogos recebidos pela API: " +
                if (disponiveis.isEmpty()) "nenhum" else disponiveis.joinToString(", ")
        )
    }

    /**
     * Consulta histórico. Informe o gameId obtido no resultado ao vivo.
     * Se a conta tiver apenas um jogo crash, a API permite omitir game.
     */
    fun fetchAirJetHistory(
        gameId: String,
        limit: Int = 50,
        cursor: String? = null
    ): History {
        require(gameId.isNotBlank()) { "O game_id do AirJet é obrigatório." }
        require(limit in 1..1000) { "O limite deve ficar entre 1 e 1000." }

        val query = buildString {
            append("?game=").append(URLEncoder.encode(gameId, Charsets.UTF_8.name()))
            append("&limit=").append(limit)
            if (!cursor.isNullOrBlank()) {
                append("&cursor=").append(URLEncoder.encode(cursor, Charsets.UTF_8.name()))
            }
        }
        val json = get("/v1/crash/history$query")
        require(json.optString("status") == "ok") {
            "A API não confirmou status ok no histórico."
        }
        val returnedGameId = json.optString("game_id")
        require(returnedGameId == gameId) {
            "O histórico retornou outro jogo: ${returnedGameId.ifBlank { "desconhecido" }}."
        }
        val array = json.optJSONArray("rounds")
            ?: throw IllegalStateException("A API não retornou a lista rounds.")
        val rounds = buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                // Ignora itens sem multiplicador válido; a API pode evoluir seu formato.
                val multiplier = item.optDouble("multiplier", Double.NaN)
                val id = item.optString("round_id")
                if (multiplier.isFinite() && multiplier >= 1.0 && id.isNotBlank()) {
                    add(parseRound(item))
                }
            }
        }
        return History(
            gameId = returnedGameId,
            rounds = rounds,
            nextCursor = json.optString("next_cursor").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private fun parseRound(json: JSONObject): Round {
        val multiplier = json.optDouble("multiplier", Double.NaN)
        require(multiplier.isFinite() && multiplier >= 1.0) {
            "Multiplicador ausente ou inválido na resposta."
        }
        val roundId = json.optString("round_id")
        require(roundId.isNotBlank()) { "A rodada veio sem round_id." }
        return Round(
            roundId = roundId,
            multiplier = multiplier,
            timestamp = json.optString("timestamp").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private fun get(pathAndQuery: String): JSONObject {
        val connection = URL(BASE_URL + pathAndQuery).openConnection() as HttpURLConnection
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
