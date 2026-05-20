package com.offbuss.data

import android.content.Context
import com.google.gson.Gson
import com.offbuss.model.Horarios

class RouteRepository(private val context: Context) {

    private val gson = Gson()

    /**
     * Índice de linhas disponíveis no app.
     * Para adicionar uma nova linha: copie os .geojson para assets/routes/,
     * crie o .json de horários em assets/horarios/ e adicione uma entrada aqui.
     */
    private val linhasDisponiveis = mapOf(
        "116" to Triple("Terminal Ouro Verde", "Shopping Dom Pedro",       true),
        "117" to Triple("Terminal Ouro Verde", "Jardim Itatinga",          true),
        "138" to Triple("Terminal Ouro Verde", "Recanto do Sol I e II",    true)
    )

    /** Busca por número da linha, ponto inicial ou final (case-insensitive) */
    fun search(termo: String): List<String> {
        val t = termo.trim().lowercase()
        return linhasDisponiveis.entries
            .filter { (linha, info) ->
                linha.contains(t) ||
                        info.first.lowercase().contains(t) ||
                        info.second.lowercase().contains(t)
            }
            .map { it.key }
            .sorted()
    }

    fun temSentido2(linha: String): Boolean =
        linhasDisponiveis[linha]?.third ?: false

    fun getPontos(linha: String): Pair<String, String> {
        val info = linhasDisponiveis[linha] ?: return Pair("", "")
        return Pair(info.first, info.second)
    }

    fun loadGeoJsonRota(linha: String, sentido: Int): String? =
        readAsset("routes/${linha}_sentido${sentido}_rota.geojson")

    fun loadGeoJsonPontos(linha: String, sentido: Int): String? =
        readAsset("routes/${linha}_sentido${sentido}_pontos.geojson")

    fun loadHorarios(linha: String, sentido: Int): Horarios? {
        val json = readAsset("horarios/${linha}_sentido${sentido}.json") ?: return null
        return runCatching { gson.fromJson(json, Horarios::class.java) }.getOrNull()
    }

    private fun readAsset(path: String): String? =
        runCatching {
            context.assets.open(path).bufferedReader().use { it.readText() }
        }.getOrNull()
}