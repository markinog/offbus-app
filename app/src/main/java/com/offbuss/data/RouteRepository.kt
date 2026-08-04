package com.offbuss.data

import android.content.Context
import com.google.gson.Gson
import com.offbuss.model.Horarios
import com.offbuss.model.Route
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

class RouteRepository(private val context: Context) {

    private val gson = Gson()

    /**
     * Índice de linhas disponíveis no app.
     * Para adicionar uma nova linha: copie os .geojson para assets/routes/,
     * crie o .json de horários em assets/horarios/ e adicione uma entrada aqui.
     *
     * O terceiro campo da Triple indica se a linha tem volta. Só marque true
     * quando o GeoJSON realmente trouxer features com "sentido": 2 — as linhas
     * circulares têm apenas o sentido 1, e o botão de volta ficaria vazio.
     *
     * Os pontos das linhas a partir da 135 vieram do "nome_linha" do próprio
     * GeoJSON: "A / B" virou (A, B) e, quando o nome era único, o extremo da
     * volta veio do nome do sentido 2. O campo é sujo na origem — abreviações,
     * qualificadores como "(INCLUSIVO)" e alguns nomes numéricos —, então há
     * nomes menos precisos que os das 21 primeiras linhas, que foram curadas
     * à mão. Horários existem apenas para essas 21.
     */
    private val linhasDisponiveis = mapOf(
        "101" to Triple("Parque Universitário",  "Parque Universitário",   false),
        "102" to Triple("Recanto do Sol",        "Recanto do Sol",         false),
        "105" to Triple("Cohab I",               "Cohab I",                false),
        "106" to Triple("Cohab II",              "Cohab II",               false),
        "108" to Triple("Jardim Aeronave I",     "Jardim Aeronave I",      false),
        "109" to Triple("DIC VI",                "Vila Aeroporto",         false),
        "110" to Triple("Adhemar de Barros",     "Adhemar de Barros",      false),
        "111" to Triple("Jardim Aeronave II",    "Jardim Aeronave II",     false),
        "112" to Triple("Jardim Planalto I",     "Jardim Planalto I",      false),
        "113" to Triple("Jardim Planalto II",    "Jardim Planalto II",     false),
        "114" to Triple("Jardim Melina",         "Corredor Central",       true),
        "116" to Triple("Terminal Ouro Verde",   "Shopping Dom Pedro",     true),
        "117" to Triple("Terminal Ouro Verde",   "Jardim Itatinga",        true),
        "122" to Triple("Terminal Vida Nova",    "Campinas Shopping",      true),
        "123" to Triple("Terminal Campo Grande", "Terminal Ouro Verde",    true),
        "125" to Triple("Terminal Ouro Verde",   "Shopping Iguatemi",      true),
        "128" to Triple("Vida Nova",             "Terminal Mercado I",     true),
        "131" to Triple("Vida Nova",             "Terminal Mercado I",     true),
        "133" to Triple("Vida Nova",             "Vida Nova",              true),
        "134" to Triple("Terminal Ouro Verde",   "Terminal Ouro Verde",    true),
        "135" to Triple("Jardim San Diego",      "Jardim San Diego",       true),
        "136" to Triple("Vida Nova",             "Vida Nova",              true),
        // Circular: o GeoJSON da 138 só traz features de sentido 1
        "138" to Triple("Terminal Ouro Verde",   "Recanto do Sol I e II",  false),
        "144" to Triple("Jardim Capivari",       "Jardim Capivari",        false),
        // O nome_linha da 150 vem como "999" no GeoJSON; os pontos saíram das
        // primeira e última paradas do sentido 1
        "150" to Triple("Ruy Rodriguez",         "Terminal Vila União",    true),
        "152" to Triple("Terminal Vila União III", "Terminal Vila União III", false),
        "153" to Triple("Terminal Vila União",   "Terminal Vila União",    true),
        "154" to Triple("Terminal Vila União",   "Terminal Vila União",    true),
        "161" to Triple("Terminal Vida Nova",    "Terminal Vida Nova",     true),
        "163" to Triple("Terminal Vila União",   "Terminal Central",       true),
        "164" to Triple("Terminal Vila União",   "Terminal Central",       true),
        "165" to Triple("Jardim Paulicéia",      "Jardim Paulicéia",       false),
        "171" to Triple("Vila Palmeiras",        "Vila Palmeiras",         true),
        "173" to Triple("Vila Formosa",          "Parque Itália",          true),
        "179" to Triple("Jardim São Domingos",   "Terminal Mercado I",     true),
        "180" to Triple("Jardim Santo Antônio",  "Jardim Santo Antônio",   false),
        "186" to Triple("Jardim Santa Maria",    "Jardim Santa Maria",     true),
        "187" to Triple("São Domingos",          "Terminal Ouro Verde",    true),
        "188" to Triple("Terminal Central",      "Terminal Ouro Verde",    true),
        "189" to Triple("Vila Diva",             "Vila Diva",              true),
        "190" to Triple("Jardim São Domingos",   "Jardim São Domingos",    true),
        "191" to Triple("Jardim Fernanda",       "Jardim Fernanda",        true),
        "192" to Triple("Vila Diva",             "Vila Diva",              true),
        "193" to Triple("Terminal Barão Geraldo", "Rodoviária",            true),
        "194" to Triple("Terminal Central",      "Terminal Central",       true),
        "195" to Triple("Vila Palmeiras",        "Vila Palmeiras",         true),
        "196" to Triple("Terminal Ouro Verde",   "Aeroporto de Viracopos", true),
        "197" to Triple("Vila Diva",             "Vila Diva",              true),
        "198" to Triple("Terminal Ouro Verde",   "Terminal Ouro Verde",    true),
        "199" to Triple("Vida Nova",             "Terminal Mercado I",     true),
        "200" to Triple("Terminal Campo Grande", "Terminal Campo Grande",  false),
        "201"   to Triple("Jardim Santa Rosa",                  "Jardim Santa Rosa",                  false),
        "202"   to Triple("Parque Valença II",                  "Parque Valença II",                  false),
        "203"   to Triple("Campina Grande",                     "Campina Grande",                     false),
        "204"   to Triple("Parque da Floresta",                 "Parque da Floresta",                 false),
        "205"   to Triple("Terminal Campo Grande",              "Terminal Ouro Verde",                true),
        "206"   to Triple("Santa Clara",                        "Santa Clara",                        false),
        "207"   to Triple("Jardim Novo Mundo",                  "Jardim Novo Mundo",                  false),
        "208"   to Triple("Terminal Itajaí",                    "Terminal Itajaí",                    false),
        "209"   to Triple("Chácara Cruzeiro do Sul",            "Chácara Cruzeiro do Sul",            false),
        "209.1" to Triple("Chácara Cruzeiro do Sul",            "Chácara Cruzeiro do Sul",            false),
        "210"   to Triple("Terminal Campo Grande",              "Terminal Ouro Verde",                true),
        "211"   to Triple("Terminal Ouro Verde",                "Vila Esperança",                     true),
        "212"   to Triple("Terminal Itajaí",                    "Terminal Itajaí",                    true),
        "213"   to Triple("Vida Nova",                          "Terminal Ouro Verde",                true),
        "214"   to Triple("Terminal Itajaí",                    "Vila Padre Manoel da Nóbrega I",     true),
        "215"   to Triple("Terminal Itajaí",                    "Terminal Itajaí",                    false),
        "216"   to Triple("Terminal Itajaí",                    "Terminal Campo Grande",              false),
        "217"   to Triple("Terminal Itajaí",                    "Terminal Itajaí",                    false),
        "218"   to Triple("Terminal Itajaí",                    "Terminal Campo Grande",              true),
        "220"   to Triple("Vila Padre Manoel da Nóbrega I",     "Terminal Ouro Verde",                true),
        "226"   to Triple("Residencial Sírius II",              "Residencial Sírius II",              false),
        "227"   to Triple("Jardim Florence II",                 "Jardim Florence II",                 false),
        "230"   to Triple("Ipaussurama",                        "Terminal Ouro Verde",                true),
        "231"   to Triple("Shopping Dom Pedro",                 "Terminal Ouro Verde",                true),
        "232"   to Triple("Satélite Íris IV",                   "Satélite Íris IV",                   false),
        "233"   to Triple("Jardim Florence I",                  "Jardim Florence I",                  false),
        "239"   to Triple("Terminal Ouro Verde",                "Terminal Ouro Verde",                true),
        "240"   to Triple("Vila Teixeira",                      "Terminal Vida Nova",                 true),
        "241"   to Triple("Vila Padre Manoel da Nóbrega I",     "Vila Padre Manoel da Nóbrega I",     true),
        "242"   to Triple("Terminal Barão Geraldo",             "Vila Padre Anchieta",                true),
        "244"   to Triple("Vila Teixeira",                      "Shopping Dom Pedro",                 true),
        "249"   to Triple("Shopping Dom Pedro",                 "Shopping Iguatemi",                  true),
        "251"   to Triple("Terminal Vida Nova",                 "Terminal Metropolitano",             true),
        "252"   to Triple("Parque São Jorge",                   "Parque São Jorge",                   true),
        "253"   to Triple("Swift",                              "Vila Orosimbo Maia",                 true),
        "254"   to Triple("Terminal Padre Anchieta",            "Terminal Padre Anchieta",            true),
        "255"   to Triple("Swift",                              "Vila Boa Vista",                     true),
        "256"   to Triple("Parque São Jorge",                   "Vila Formosa",                       true),
        "257"   to Triple("Parque São Jorge",                   "Terminal Mercado I",                 true),
        "260"   to Triple("Vila Marieta",                       "Estação Cidade Judiciária",          true),
        "261"   to Triple("Vila Réggio",                        "Vila Padre Anchieta",                true),
        "263"   to Triple("Vila Padre Anchieta",                "Terminal Padre Anchieta",            true),
        "264"   to Triple("Vila Teixeira",                      "Vila Padre Anchieta",                true),
        "265"   to Triple("Padre Anchieta",                     "Padre Anchieta",                     true),
        "266"   to Triple("Shopping Dom Pedro",                 "Shopping Iguatemi",                  true),
        "269"   to Triple("Terminal Padre Anchieta",            "Terminal Barão Geraldo",             true),
        "271"   to Triple("Padre Anchieta",                     "Vila Orosimbo Maia",                 true),
        "273"   to Triple("Swift",                              "Vila Padre Anchieta",                true),
        "289"   to Triple("Terminal Campo Grande",              "Terminal Ouro Verde",                true),
        "299"   to Triple("Nova Aparecida",                     "Nova Aparecida",                     true),
        "300"   to Triple("Sousas",                             "Terminal Barão Geraldo",             true),
        "309"   to Triple("Parque Cidade",                      "Terminal Mercado I",                 true),
        "310"   to Triple("Vila Olímpia",                       "Vila Olímpia",                       true),
        "311"   to Triple("Vila Marieta",                       "Shopping Dom Pedro",                 true),
        "312"   to Triple("Vila Esperança",                     "Vila Esperança",                     true),
        "313"   to Triple("CDHU",                               "Amarais",                            true),
        "314"   to Triple("Terminal Padre Anchieta",            "Terminal Barão Geraldo",             true),
        "315"   to Triple("San Martin",                         "CDHU Amarais",                       true),
        "316"   to Triple("Parque Cidade",                      "Parque Cidade",                      true),
        "317"   to Triple("Jardim São José",                    "Jardim São Marcos",                  true),
        "318"   to Triple("Jardim Mirassol",                    "Vila Orosimbo Maia",                 true),
        "319"   to Triple("Parque Cidade",                      "Terminal Barão Geraldo",             true),
        "320"   to Triple("Real Parque II",                     "Real Parque II",                     false),
        "321"   to Triple("Jockey Club",                        "Jockey Club",                        false),
        "322"   to Triple("Village Campinas",                   "Village Campinas",                   false),
        "323"   to Triple("Vila Holândia",                      "Vila Holândia",                      false),
        "324"   to Triple("Vila Santa Isabel",                  "Terminal Barão Geraldo",             false),
        "325"   to Triple("Vila Santa Isabel",                  "Terminal Barão Geraldo",             false),
        "328"   to Triple("Parque dos Alecrins",                "Cidade Judiciária",                  true),
        "329"   to Triple("Terminal Barão Geraldo",             "Cidade Judiciária",                  true),
        "329.1" to Triple("Vila Marieta",                       "Estação Cidade Judiciária",          true),
        "330"   to Triple("UNICAMP",                            "UNICAMP",                            true),
        "331"   to Triple("Vila Formosa",                       "Vila Orosimbo Maia",                 true),
        "332"   to Triple("Jardim Garcia",                      "Shopping Dom Pedro",                 true),
        "333"   to Triple("Terminal Barão Geraldo",             "Terminal Barão Geraldo",             true),
        "336"   to Triple("Terminal Barão Geraldo",             "Hospital das Clínicas",              false),
        "337"   to Triple("Terminal Barão Geraldo",             "UNICAMP",                            false),
        "338"   to Triple("Terminal Barão Geraldo",             "Shopping Iguatemi",                  true),
        "339"   to Triple("Cidade Universitária",               "Terminal Mercado I",                 true),
        "341"   to Triple("Parque Jambeiro",                    "Vila Formosa",                       true),
        "342"   to Triple("Leroy Merlin",                       "Terminal Vida Nova",                 true),
        "344"   to Triple("Vila Marieta",                       "Estação Parque Prado",               true),
        "345"   to Triple("Vila Marieta",                       "Estação Cidade Judiciária",          true),
        "346"   to Triple("Jardim Carlos Lourenço",             "Jardim Carlos Lourenço",             true),
        "348"   to Triple("Vila Marieta",                       "Estação Cidade Judiciária",          true),
        "349"   to Triple("Vila Formosa",                       "Vila Formosa",                       true),
        "350"   to Triple("Vila Marieta",                       "Estação Cidade Judiciária",          true),
        "351"   to Triple("Recanto dos Dourados",               "Estação Cidade Judiciária",          true),
        "352"   to Triple("Jardim São Vicente",                 "Parque Itália",                      true),
        "353"   to Triple("Ipaussurama",                        "Parque das Resedás",                 true),
        "355"   to Triple("Vila Gênesis",                       "Vila Gênesis",                       true),
        "356"   to Triple("Recanto dos Dourados",               "Estação Cidade Judiciária",          true),
        "357"   to Triple("PUCC",                               "PUCC",                               true),
        "358"   to Triple("Terminal Barão Geraldo",             "Cidade Judiciária",                  true),
        "359"   to Triple("Jardim Esmeraldina",                 "Estação Cidade Judiciária",          true),
        "360"   to Triple("Proença",                            "Castelo",                            true),
        "361"   to Triple("Vila Marieta",                       "Shopping Dom Pedro",                 true),
        "362"   to Triple("Terminal Vida Nova",                 "Parque Brasília via Taquaral",       true),
        "364"   to Triple("Shopping Dom Pedro",                 "Shopping Iguatemi",                  true),
        "366"   to Triple("Vila Orosimbo Maia",                 "Vila Orosimbo Maia",                 true),
        "367"   to Triple("Jardim Baronesa",                    "Parque São Jorge",                   true),
        "368"   to Triple("Notre Dame",                         "Parque São Jorge",                   true),
        "369"   to Triple("Parque Imperador",                   "Vila 31 de Março",                   true),
        "371"   to Triple("Vila Marieta",                       "Estação Cidade Judiciária",          true),
        "375"   to Triple("Sousas",                             "Terminal Barão Geraldo",             true),
        "377"   to Triple("Vila Marieta",                       "Shopping Dom Pedro",                 true),
        "380"   to Triple("Terminal Central",                   "Vila Olímpia",                       true),
        "381"   to Triple("Shopping Dom Pedro",                 "Shopping Iguatemi",                  true),
        "383"   to Triple("Leroy Merlin",                       "Rodoviária",                         true),
        "383.1" to Triple("Leroy Merlin",                       "Rodoviária",                         true),
        "384"   to Triple("Leroy Merlin",                       "Rodoviária",                         true),
        "385"   to Triple("Shopping Iguatemi",                  "Rodoviária",                         true),
        "386"   to Triple("Rodoviária",                         "Terminal Vida Nova",                 true),
        "389"   to Triple("Estação Sousas",                     "Estação Sousas",                     true),
        "390"   to Triple("Terminal Central",                   "Sousas",                             true),
        "391"   to Triple("Nova Sousas",                        "Vila Esperança",                     true),
        "392"   to Triple("San Conrado",                        "Vila Diva",                          true),
        "393"   to Triple("Cabras",                             "Estação Sousas",                     true),
        "394"   to Triple("Parque Jatibaia",                    "Parque Jatibaia",                    true),
        "394.1" to Triple("Parque Jatibaia",                    "Parque Jatibaia",                    true),
        "395"   to Triple("Notre Dame",                         "Notre Dame",                         true),
        "396"   to Triple("Sousas",                             "Sousas",                             true),
        "397"   to Triple("Leroy Merlin",                       "Rodoviária",                         true),
        "398"   to Triple("Joaquim Egídio",                     "Joaquim Egídio",                     true),
        "399"   to Triple("Joaquim Egídio",                     "Terminal Mercado I",                 true),
        "401"   to Triple("Parque Figueira",                    "Swiss Park",                         true),
        "402"   to Triple("Parque Figueira",                    "Swiss Park",                         true),
        "403"   to Triple("Swiss Park",                         "Swiss Park",                         true),
        "404"   to Triple("Jardim Petrópolis",                  "Jardim Petrópolis",                  true),
        "405"   to Triple("Jardim Petrópolis",                  "Jardim Petrópolis",                  true),
        "406"   to Triple("Jardim Petrópolis",                  "Jardim Petrópolis",                  false),
        "408"   to Triple("Terminal Barão Geraldo",             "Rodoviária",                         true),
        "409"   to Triple("Vila Orosimbo Maia",                 "Vila Orosimbo Maia",                 true),
        "410"   to Triple("Jardim San Diego",                   "Jardim San Diego",                   true),
        "411"   to Triple("Parque Oziel via Jardim do Lago II", "Parque Oziel via Jardim do Lago II", true),
        "412"   to Triple("Parque Eldorado",                    "Parque Eldorado",                    true),
        "413"   to Triple("Parque Oziel via Jardim do Lago II", "Parque Oziel via Jardim do Lago II", true),
        "414"   to Triple("Parque Oziel",                       "Parque Oziel",                       true),
        "415"   to Triple("Saltinho",                           "Parque Oziel via Jardim do Lago II", true),
        "416"   to Triple("Rodoviária",                         "Vila Orosimbo Maia",                 true),
        "422"   to Triple("Jardim Nova América",                "Terminal Central",                   true),
        "429"   to Triple("Swiss Park",                         "Swiss Park",                         true),
        "430"   to Triple("Swiss Park",                         "Vila Palmeiras",                     true),
        "431"   to Triple("Saltinho",                           "Reforma Agrária",                    true),
        "432"   to Triple("Saltinho",                           "Saltinho",                           true),
        "433"   to Triple("Saltinho",                           "Nossa Senhora de Lourdes",           true),
        "434"   to Triple("Parque Figueira",                    "Swiss Park",                         true),
        "489"   to Triple("Parque Jambeiro",                    "Terminal Mercado I",                 true),
        "499"   to Triple("Vila Marieta",                       "Estação Cidade Judiciária",          true),
        "502"   to Triple("Vila Orosimbo Maia",                 "Vila Orosimbo Maia",                 false)
        // A 265.1 não entra: o _sentido1_rota.geojson dela só traz features
        // de sentido 2, e a busca sempre abre a linha pela ida
    )

    /**
     * Busca por número da linha, ponto inicial ou final (case-insensitive).
     *
     * Devolve a [Route] inteira, e não só o número, porque a lista de
     * resultados mostra os pontos ao lado da linha — sem eles o usuário não
     * teria como escolher entre as quatro linhas que atendem "Vida Nova".
     *
     * Termo vazio devolve lista vazia: casar substring com "" traria o índice
     * inteiro de uma vez, o que não é resultado de busca nenhum.
     */
    fun search(termo: String): List<Route> {
        val t = semAcento(termo)
        if (t.isEmpty()) return emptyList()

        return linhasDisponiveis.entries
            .filter { (linha, info) ->
                linha.contains(t) ||
                        semAcento(info.first).contains(t) ||
                        semAcento(info.second).contains(t)
            }
            .sortedBy { it.key.toDouble() }
            .map { (linha, info) -> Route(linha, info.first, info.second, info.third) }
    }

    /**
     * Minúsculas e sem acento, dos dois lados da comparação.
     *
     * O índice está cheio de "União", "Itajaí" e "Judiciária", e ninguém digita
     * acento no teclado do celular para buscar um ônibus.
     */
    private fun semAcento(texto: String): String =
        Normalizer.normalize(texto.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    fun temSentido2(linha: String): Boolean =
        linhasDisponiveis[linha]?.third ?: false

    fun getPontos(linha: String): Pair<String, String> {
        val info = linhasDisponiveis[linha] ?: return Pair("", "")
        return Pair(info.first, info.second)
    }

    fun loadGeoJsonRota(linha: String, sentido: Int): String? {
        val json = readAsset("routes/${linha}_sentido${sentido}_rota.geojson") ?: return null
        // GeoJSON inesperado: devolve o arquivo cru em vez de sumir com a rota
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return json
        return filtrarPorSentido(root, sentido)?.toString()
    }

    /**
     * Lê as paradas do sentido e injeta a propriedade "ordem" em cada feature,
     * usada como rótulo numérico no mapa.
     *
     * A numeração segue a posição da parada ao longo do traçado, e não a ordem
     * em que ela aparece no arquivo — ver [OrdenadorDeParadas].
     */
    fun loadGeoJsonPontos(linha: String, sentido: Int): String? {
        val json = readAsset("routes/${linha}_sentido${sentido}_pontos.geojson") ?: return null
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return json
        val doSentido = filtrarPorSentido(root, sentido) ?: return null

        val paradas = doSentido.optJSONArray("features") ?: return null
        val ordem = runCatching {
            val rota = readAsset("routes/${linha}_sentido${sentido}_rota.geojson")
                ?.let { JSONObject(it) }
                ?.let { filtrarPorSentido(it, sentido) }
                ?.optJSONArray("features")
            rota?.let { OrdenadorDeParadas.ordem(paradas, it) }
        }.getOrNull()

        return runCatching { numerarPontos(doSentido, ordem).toString() }.getOrNull()
    }

    /**
     * Mantém só as features do sentido pedido.
     *
     * Os arquivos da EMDEC trazem ida e volta no mesmo GeoJSON, cada feature
     * marcada com a propriedade "sentido". Sem este filtro o mapa desenha os
     * dois trajetos sobrepostos e as paradas de ambos.
     *
     * Devolve null quando o sentido não existe no arquivo — caso das linhas
     * circulares, que só têm sentido 1.
     */
    private fun filtrarPorSentido(root: JSONObject, sentido: Int): JSONObject? {
        val features = root.optJSONArray("features") ?: return null
        val filtradas = JSONArray()

        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            if (feature.optJSONObject("properties")?.optInt("sentido", -1) == sentido) {
                filtradas.put(feature)
            }
        }

        if (filtradas.length() == 0) return null
        return root.put("features", filtradas)
    }

    /**
     * Numera as paradas de 1 a n.
     *
     * [ordem] traz os índices na sequência do percurso, calculada a partir do
     * traçado. Quando é null — traçado inutilizável ou linha que refaz o mesmo
     * corredor nos dois sentidos — cai para a ordem do próprio arquivo.
     */
    private fun numerarPontos(root: JSONObject, ordem: List<Int>?): JSONObject {
        val features = root.getJSONArray("features")
        val sequencia = ordem?.takeIf { it.size == features.length() }
            ?: List(features.length()) { it }

        sequencia.forEachIndexed { posicao, indice ->
            val feature = features.getJSONObject(indice)
            val props   = feature.optJSONObject("properties")
                ?: JSONObject().also { feature.put("properties", it) }

            // Gravado como texto: o text-field do MapLibre espera string
            props.put("ordem", (posicao + 1).toString())
        }
        return root
    }

    fun loadHorarios(linha: String, sentido: Int): Horarios? {
        val json = readAsset("horarios/${linha}_sentido${sentido}.json") ?: return null
        return runCatching { gson.fromJson(json, Horarios::class.java) }.getOrNull()
    }

    private fun readAsset(path: String): String? =
        runCatching {
            context.assets.open(path).bufferedReader().use { it.readText() }
        }.getOrNull()
}