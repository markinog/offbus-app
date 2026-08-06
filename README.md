# OffBus — Consulta Offline de Ônibus em Campinas

> Trabalho de Conclusão de Curso · IFSP — Campus Campinas
> Discente: **Marcus Vinicius de Souza Gomes** · Orientadora: **Prof. Msa. Zady Castañeda Salazar**

Aplicativo Android nativo, em Kotlin, que mostra **rotas, paradas e horários** dos ônibus municipais de Campinas/SP **com o celular em modo avião**. Não há backend, não há API, não há cache que expira: o mapa de Campinas inteiro, os traçados das 205 linhas e as tabelas de horário viajam dentro do APK.

O único endereço que o app acessa é `127.0.0.1`.

---

## Índice

- [Por que offline](#por-que-offline)
- [O app em 30 segundos](#o-app-em-30-segundos)
- [Anatomia de uma busca](#anatomia-de-uma-busca)
- [De uma linha escolhida até o traçado na tela](#de-uma-linha-escolhida-até-o-traçado-na-tela)
- [Numerando as paradas na ordem certa](#numerando-as-paradas-na-ordem-certa)
- [O mapa offline](#o-mapa-offline)
- [Horários](#horários)
- [Os dados](#os-dados)
- [Decisões de projeto](#decisões-de-projeto)
- [Build e execução](#build-e-execução)
- [Como adicionar uma nova linha](#como-adicionar-uma-nova-linha)
- [Stack técnica](#stack-técnica)
- [Estrutura do projeto](#estrutura-do-projeto)

---

## Por que offline

Cittamobi, Moovit e afins pedem conectividade constante para responder a uma pergunta simples: *a que horas passa o 116?* No Brasil, para quem depende de plano pré-pago, a franquia acabar significa perder o acesso à informação mais básica do transporte público — uma forma concreta de **exclusão digital**, e ela bate justamente em quem mais usa ônibus.

O OffBus inverte a premissa. Em vez de buscar o dado na hora, ele já chegou com o dado. É **offline-first** no sentido literal: não existe caminho de código que faça uma requisição para fora do dispositivo.

**Efeitos colaterais agradáveis:** funciona no subsolo do terminal, não gasta bateria com rádio, não pede GPS, não pede login, não coleta nada e responde instantaneamente — a busca percorre um mapa em memória, não uma rede.

---

## O app em 30 segundos

Uma tela só. Um campo de texto, um mapa e três botões que aparecem quando fazem sentido.

```
┌─────────────────────────────────┐
│  🔍 [ ouro verde            ]   │  ← busca ao vivo, a cada tecla
├─────────────────────────────────┤
│  101   Parque Universitário /…  │  ← até 5 visíveis, o resto rola
│  102   Recanto do Sol / Term…   │
│  105   Cohab I / Terminal Our…  │
├─────────────────────────────────┤
│                                 │
│         ▓▓▓ MAPA ▓▓▓            │  ← traçado + paradas enumeradas
│      ①──②──③──④──⑤──⑥          │     toque numa bolinha → detalhes
│                                 │
├─────────────────────────────────┤
│  [ Ida ] [ Volta ]  [Horários]  │
└─────────────────────────────────┘
```

Antes da primeira busca o mapa fica oculto e o espaço é da dica de uso. Depois da primeira busca ele nunca mais some — o espaço passa a ser dele.

Fluxo completo: **digitar → escolher na lista → ver o traçado → alternar ida/volta → abrir horários → tocar numa parada.**

---

## Anatomia de uma busca

Toda a busca vive em `RouteRepository.search()`, sobre um `Map` de 205 entradas declarado no próprio código:

```kotlin
private val linhasDisponiveis = mapOf(
    "332" to Triple("Rodoviária", "Hospital das Clínicas", true),
    //     ↑ ida        ↑ volta      ↑ tem sentido 2?
)
```

### 1. O termo é achatado

```kotlin
private fun semAcento(texto: String): String =
    Normalizer.normalize(texto.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
```

`Normalizer.Form.NFD` decompõe `"í"` em `"i"` + acento combinante; a regex `\p{Mn}+` (*Mark, nonspacing*) varre os acentos e sobra o esqueleto ASCII. O índice está cheio de **União**, **Itajaí**, **Judiciária** e **Paulicéia** — e ninguém digita acento no teclado do celular correndo para o ponto.

A mesma função é aplicada nos dois lados da comparação, então `"jardim são josé"`, `"JARDIM SAO JOSE"` e `"jd sao jose"` (esta última só se o nome também tiver a abreviação) convergem para a mesma forma.

### 2. Três campos, um filtro

```kotlin
linha.contains(t) || semAcento(info.first).contains(t) || semAcento(info.second).contains(t)
```

Número, ponto de ida e ponto de volta valem igual, e a comparação é por **substring**, não por prefixo. Isso é deliberado:

| Você digita | Casa com | Por quê |
|---|---|---|
| `332` | 332 | número exato |
| `33` | 133, 233, 330–339, 433… | substring no número |
| `verde` | todas as que tocam Terminal Ouro Verde | substring no ponto |
| `barao` | Terminal Barão Geraldo, nos dois sentidos | sem acento |
| `unicamp` | 330, 337 | ponto de volta |

Quem só sabe *"aquele que vai pro Iguatemi"* chega lá sem saber o número — que é exatamente o usuário que um app de ônibus precisa atender.

### 3. Termo vazio devolve lista vazia

```kotlin
if (t.isEmpty()) return emptyList()
```

Sem essa guarda, `"".contains("")` seria `true` para todas as entradas e apagar o campo despejaria o índice inteiro na tela. Lista vazia é a resposta honesta para uma pergunta que não foi feita.

### 4. Ordenação numérica

```kotlin
.sortedBy { it.key.toDouble() }
```

Ordenar texto colocaria `1000` antes de `999`. Como `Double`, a sequência sai natural — e os sufixos que a EMDEC usa para variantes (`209.1`, `329.1`, `383.1`, `394.1`) caem exatamente onde deveriam: logo depois da linha-mãe.

### 5. Volta a `Route` inteira, não só o número

```kotlin
.map { (linha, info) -> Route(linha, info.first, info.second, info.third) }
```

A lista de resultados mostra os extremos ao lado do número — sem eles não haveria como escolher entre as quatro linhas que atendem "Vida Nova". E quando ida e volta coincidem (circulares), a UI mostra o nome uma vez só em vez de repetir:

```kotlin
if (rota.pontoInicial.equals(rota.pontoFinal, ignoreCase = true)) rota.pontoInicial
else "${rota.pontoInicial} / ${rota.pontoFinal}"
```

### 6. Na tela: ao vivo, mas nunca sozinha

`doAfterTextChanged` chama a busca a cada tecla — sem *debounce*, porque filtrar 205 entradas em memória é mais rápido que o próximo toque. E a lista **nunca abre uma linha sozinha**, nem quando sobra um único resultado:

> se a busca por `116` carregasse o mapa automaticamente, quem estivesse digitando `1163` veria o mapa piscar no meio da palavra.

A escolha é sempre um toque. O botão da lupa não faz uma busca diferente da que já está acontecendo — ele só recolhe o teclado para a lista aparecer inteira.

---

## De uma linha escolhida até o traçado na tela

Toque num resultado e sete coisas acontecem, nesta ordem:

```
exibirResultado("332")
 ├─ currentLinha = "332", currentSentido = 1
 ├─ botão "Volta" visível? → repository.temSentido2("332")
 ├─ teclado recolhido, lista escondida, mapa revelado
 └─ carregarSentido()
      ├─ loadGeoJsonRota("332", 1)    → routes/332_sentido1_rota.geojson
      ├─ loadGeoJsonPontos("332", 1)  → routes/332_sentido1_pontos.geojson
      └─ renderizarRota(rota, pontos)
           ├─ sources/layers: cria na 1ª vez, só troca o GeoJSON depois
           └─ enquadrarMapa() → bounding box + 80 px de margem
```

### O filtro de sentido

Os arquivos da EMDEC trazem **ida e volta no mesmo GeoJSON**, cada feature marcada com `"sentido": 1` ou `2`. Sem filtrar, o mapa desenharia os dois trajetos sobrepostos e o dobro das paradas:

```kotlin
if (feature.optJSONObject("properties")?.optInt("sentido", -1) == sentido) {
    filtradas.put(feature)
}
```

Quando o sentido pedido não existe no arquivo — o caso das circulares, que só têm sentido 1 — o filtro devolve `null` e a UI avisa em vez de desenhar um mapa vazio. É por isso que o terceiro campo da `Triple` existe: o botão "Volta" **só aparece** para linhas que realmente têm volta.

### Degradação em vez de tela branca

O repositório prefere entregar algo imperfeito a sumir com a rota:

```kotlin
val root = runCatching { JSONObject(json) }.getOrNull() ?: return json
```

GeoJSON que não parseia? Devolve o arquivo cru e deixa o MapLibre tentar. Sem paradas para o sentido? A camada recebe uma `FeatureCollection` vazia em vez de ficar exibindo as paradas da linha anterior.

### O problema do mapa que ainda não carregou

O usuário pode buscar antes de o `map.mbtiles` terminar de ser copiado. Em vez de bloquear a UI ou perder a busca, a rota fica em espera e é replayada pelo callback do estilo:

```kotlin
if (!mapReady) {
    pendingRota   = geoJsonRota
    pendingPontos = geoJsonPontos
    return
}
```

### Camadas criadas uma vez, alimentadas para sempre

Trocar de linha ou de sentido **não recria nada**. As três camadas nascem na primeira renderização e depois só recebem `setGeoJson`:

| Camada | Tipo | O que desenha |
|---|---|---|
| `route-layer` | `LineLayer` | traçado, 5 px, pontas e junções arredondadas |
| `stops-layer` | `CircleLayer` | bolinha da parada, raio interpolado por zoom (3 px no z8 → 18 px no z17) |
| `stops-number-layer` | `SymbolLayer` | o número da parada, a partir do z13 |

A cor sai de `colors.xml` (`btn_outline`, o mesmo azul que contorna o campo de busca) em vez de um hex no meio do código — mexer na paleta da UI arrasta o mapa junto, de graça.

### Enquadramento

`enquadrarMapa` percorre **todas** as features do sentido — um trajeto pode vir quebrado em vários trechos — acumulando `LatLng` de `LineString` e `MultiLineString` num `LatLngBounds`. Com dois pontos ou mais, a câmera anima até o retângulo com 80 px de respiro. Chamado dentro de `mapView.post { }`, porque antes do layout a view ainda não tem dimensão para calcular o zoom.

### Tocar numa parada

```kotlin
val alvo = RectF(x - 24f, y - 24f, x + 24f, y + 24f)
map.queryRenderedFeatures(alvo, "stops-layer")
```

A área de toque é um quadrado de 48 px — bem maior que a bolinha — para o app não exigir precisão de mouse de um dedo em movimento. O diálogo mostra o número da parada, o sentido e as linhas que passam por ali.

Detalhe defensivo: 218 paradas da EMDEC têm `"linhas": null`. Um `getStringProperty` direto chamaria `getAsString()` sobre um `JsonNull` e lançaria `UnsupportedOperationException` — daí o acesso passar por um leitor que checa `isJsonNull` antes de converter.

---

## Numerando as paradas na ordem certa

Esta é a parte do projeto com mais geometria e a que resolve um problema que só aparece depois de olhar os dados de perto.

**O problema.** As paradas não vêm na ordem do percurso. Nem a sequência do arquivo, nem o campo `ptseqlinha` da EMDEC acompanham o trajeto — há linhas em que a numeração salta entre ruas vizinhas e volta. Numerar as bolinhas na ordem do arquivo produziria um mapa com "parada 7" antes da "parada 3".

**A solução** (`OrdenadorDeParadas`): esquecer a numeração declarada e medir. Cada parada é projetada sobre a geometria da rota; a ordem é a distância percorrida ao longo do traçado até o pé da projeção.

```
     ①      ②        ③         ④
─────●──────●────────●─────────●──────▶  traçado costurado
     ▲      ▲        ▲         ▲
    p3     p1       p4        p2        ← ordem no arquivo
   0.0km  1.2km    2.8km     4.1km      ← distância projetada = ordem real
```

O algoritmo, em quatro passos:

1. **Achatar** — `LineString` e `MultiLineString` viram uma lista de trechos.
2. **Costurar** — os trechos vêm fora de ordem e podem estar invertidos. Partindo do trecho mais longo, a cada passo anexa-se o trecho cuja ponta esteja mais perto, testando as duas orientações e as duas extremidades do caminho já montado.
3. **Projetar** — para cada parada, encontra-se o segmento mais próximo por projeção paramétrica (`t` limitado a `0..1`, para o pé cair dentro do segmento) e guarda-se *distância acumulada* e *desvio*.
4. **Ordenar** pela distância acumulada.

### As guardas — quando o algoritmo se recusa a responder

O trecho mais importante de `OrdenadorDeParadas` é o que devolve `null`. Nesse caso o chamador mantém a ordem original do arquivo, que é a melhor informação disponível:

| Situação | Limiar | Por quê |
|---|---|---|
| Poucas paradas | `< 3` | não há ordem para descobrir |
| Traçado degenerado | `< 2` pontos, comprimento `≤ 0` | nada em que projetar |
| Paradas somam muito mais que a rota | `1.5 ×` o comprimento | a linha refaz o mesmo corredor nos dois sentidos e a EMDEC desenhou a geometria uma vez só; projetar colapsaria ida e volta, intercalando as paradas |
| Parada longe do traçado | `> 300 m` | a projeção não é confiável |
| Circuito que não fecha | `> 1200 m` entre a primeira e a última | não dá para girar a sequência com segurança |

### Duas sutilezas

**A polilinha pode ter saído ao contrário.** A costura não sabe qual ponta é o começo. O `ptseqlinha` mínimo — a origem *declarada* pela EMDEC — é usado apenas como bússola: se a origem caiu na segunda metade da sequência ordenada, a lista inteira é invertida. O dado ruim serve para decidir de que ponta contar, não em que ordem.

**Em circuito, o começo é arbitrário.** Se a origem não ficou na posição 0, a sequência é **girada** (não reordenada) para largar do ponto certo.

### E a distância?

```kotlin
val dx = (b.lon - a.lon) * 111320.0 * cos(Math.toRadians((a.lat + b.lat) / 2))
val dy = (b.lat - a.lat) * 110540.0
return hypot(dx, dy)
```

Aproximação equirretangular em vez de Haversine. Na escala de uma cidade o erro é irrelevante, e o cálculo roda dezenas de milhares de vezes por linha — `cos` e `hypot` custam muito menos que trigonometria esférica completa.

### E o número na bolinha

O resultado vira a propriedade `ordem`, **gravada como texto**:

```kotlin
props.put("ordem", (posicao + 1).toString())
```

O `text-field` do MapLibre espera string; um inteiro renderizaria vazio silenciosamente.

---

## O mapa offline

Exibir Campinas inteira sem nenhuma API externa é o outro pilar do projeto.

### 1. O `.mbtiles`

`app/src/main/assets/map.mbtiles` é um **banco SQLite** com tiles vetoriais no schema [Shortbread](https://shortbread-tiles.org/), gerados do OpenStreetMap. Cada tile é um blob PBF indexado por `(zoom_level, tile_column, tile_row)`:

```sql
SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?
```

> ⚠️ **~272 MiB, não versionado** (`*.mbtiles` está no `.gitignore`). Veja [Preparando o mapa base](#preparando-o-mapa-base).

### 2. Por que copiar para o `filesDir`

O SQLite não abre um arquivo comprimido em posição arbitrária dentro do APK. Na primeira execução o `MapStyleHelper` copia o banco para o armazenamento interno:

```kotlin
val dest = File(context.filesDir, "map.mbtiles")
if (!dest.exists()) {
    context.assets.open("map.mbtiles").use { input ->
        dest.outputStream().use { output -> input.copyTo(output) }
    }
}
```

E o `build.gradle.kts` manda o AGP não comprimir esses formatos, o que torna a cópia uma transferência direta:

```kotlin
androidResources { noCompress += listOf("mbtiles", "geojson") }
```

### 3. O servidor local de tiles

O MapLibre só sabe pedir tiles por HTTP. `TileServer` é um micro-servidor [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) em `127.0.0.1:7777`, com o banco aberto em somente leitura:

```
MapLibre  →  GET /tiles/14/9298/5671
TileServer   ├─ converte Y de XYZ para TMS
             ├─ consulta o SQLite
             ├─ descomprime gzip, se houver
             └─ devolve PBF puro
```

**Inversão TMS ↔ XYZ.** O MBTiles guarda no esquema TMS, com o eixo Y invertido em relação ao XYZ do MapLibre:

```kotlin
val tmsY = (1 shl z) - 1 - y
```

**Descompressão.** Os tiles em geral estão em gzip dentro do banco; o MapLibre quer PBF puro. A detecção é pelo *magic number*:

```kotlin
if (data.size >= 2 && data[0] == 0x1F.toByte() && data[1] == 0x8B.toByte()) {
    data = GZIPInputStream(data.inputStream()).use { it.readBytes() }
}
```

**Glifos.** O mesmo servidor entrega as fontes dos rótulos (`/fonts/noto-sans/0-255.pbf` → `assets/fonts/noto-sans/0-255.pbf`). Fonte inexistente responde **200 com corpo vazio, nunca 404** — um 404 faz o MapLibre invalidar o estilo em cascata e a tela inteira fica branca.

### 4. O estilo

`map-style.json` segue a MapLibre Style Spec e aponta para o servidor local:

```json
{
  "sources": {
    "mbtiles-source": {
      "type": "vector",
      "tiles": ["http://127.0.0.1:7777/tiles/{z}/{x}/{y}"],
      "minzoom": 0, "maxzoom": 14
    }
  },
  "glyphs": "http://127.0.0.1:7777/fonts/{fontstack}/{range}.pbf"
}
```

As camadas consomem os `source-layer` do Shortbread: `water_polygons`, `land`, `street_polygons`, `streets`, `buildings`, `place_labels`, `street_labels`.

### 5. Inicialização

```
onCreate()
 └─ getMapAsync { }                          // Dispatchers.IO
     ├─ MapStyleHelper.prepareOfflineStyle()   // copia o .mbtiles, lê o estilo
     ├─ TileServer.openAndStart()              // abre o SQLite, sobe o :7777
     └─ map.setStyle(...)                      // volta para a Main
          └─ mapReady = true                   // e renderiza a rota pendente
```

O servidor morre em `onDestroy()` via `tileServer?.stopServer()`.

### Sobre a permissão de INTERNET

O `AndroidManifest.xml` **declara** `android.permission.INTERNET` — e isso não contradiz a proposta. O Android exige a permissão para o app abrir um socket, mesmo que o socket seja loopback. O `network_security_config.xml` restringe o tráfego em texto claro exclusivamente a `127.0.0.1`: qualquer tentativa de sair do aparelho falharia por configuração, não por boa vontade.

---

## Horários

Um arquivo por linha e sentido em `assets/horarios/`:

```json
{
  "linha": "332",
  "sentido": 1,
  "pontoPartida": "Rodoviária",
  "horarios": ["05:40", "06:10", "06:40", "07:10"]
}
```

Gson desserializa na data class `Horarios` e o diálogo monta um `GridLayout` de três colunas com um cartão por horário. Quando o arquivo não existe, a tela mostra os extremos da linha e um "Horários não cadastrados" — nunca uma exceção.

---

## Os dados

| O quê | Onde | Volume |
|---|---|---|
| Índice de linhas | `RouteRepository.linhasDisponiveis` | 205 linhas |
| Traçados e paradas | `assets/routes/*.geojson` | 824 arquivos (4 por linha) |
| Horários | `assets/horarios/*.json` | 410 arquivos |
| Mapa base | `assets/map.mbtiles` | ~272 MiB (não versionado) |

### Pipeline de coleta

Os GeoJSON são extraídos do portal público da [EMDEC](https://portal.emdec.com.br) por um script Python:

```
emdec_scraper.py
 ├─ GET /itinerarios/?linha=332&sentido=1
 │    └─ localiza os <script src> com o GeoJSON embutido
 ├─ lin_itinerarios….js  →  332_sentido1_rota.geojson
 └─ pto_itinerarios….js  →  332_sentido1_pontos.geojson
```

> O script **não faz parte deste repositório** e grava num diretório fixo (`OUTPUT_DIR`). A cópia para `app/src/main/assets/routes/` é manual.

### A limpeza dos nomes

Os extremos de cada linha já vieram do `nome_linha` do GeoJSON — um campo sujo na origem, com abreviações, qualificadores como `(INCLUSIVO)` e alguns nomes puramente numéricos. O resultado eram pontos errados na lista de resultados.

Hoje eles vêm de `assets/corrigir-linhas.json`, a listagem oficial de ida/volta da EMDEC:

```json
{ "linha": "332", "Ida": "RODOVIÁRIA", "Volta": "HOSP. DAS CLÍNICAS" }
```

que vira, no índice:

```kotlin
"332" to Triple("Rodoviária", "Hospital das Clínicas", true)
```

As abreviações do arquivo (`TERM.`, `JD.`, `PQ.`, `HOSP.`) são escritas por extenso no índice, porque o nome aparece inteiro na lista de resultados — e a busca sem acento garante que ninguém precise digitá-lo assim.

---

## Decisões de projeto

Um resumo das escolhas que não são óbvias no código:

| Decisão | Alternativa descartada | Motivo |
|---|---|---|
| Índice de linhas em Kotlin | tabela SQLite ou JSON em assets | 205 entradas cabem em memória; ganha type-safety e o `temSentido2` fica declarado junto |
| Busca por substring | prefixo ou *fuzzy* | quem sabe só o destino precisa achar a linha; *fuzzy* traria ruído numa lista pequena |
| Sem *debounce* na digitação | filtrar após 300 ms | filtrar 205 entradas é mais rápido que o próximo toque |
| Nunca abrir a linha sozinha | auto-selecionar quando sobra 1 | digitar `116` não pode sequestrar quem está escrevendo `1163` |
| Teto de 5 sugestões visíveis | lista sem limite | lista e mapa dividem o mesmo `LinearLayout`; sem teto o mapa sairia da tela |
| Ordenar paradas por geometria | confiar no `ptseqlinha` | o campo da EMDEC não acompanha o percurso |
| Devolver `null` e cair na ordem do arquivo | sempre reordenar | ordem plausível é melhor que ordem confiantemente errada |
| Distância equirretangular | Haversine | erro irrelevante na escala urbana, muito mais barato |
| Tiles por HTTP local | *plugin* de fonte customizada no MapLibre | o caminho HTTP é o suportado e estável da lib |
| 200 vazio para fonte ausente | 404 | 404 invalida o estilo inteiro no MapLibre |

---

## Build e execução

**Requisitos:** Android Studio · JDK 17 · aparelho ou emulador com Android 8.0 (API 26) ou superior.

```bash
git clone https://github.com/markinog/poc-tcc.git
cd poc-tcc

# coloque o map.mbtiles em app/src/main/assets/ (veja abaixo)

./gradlew assembleDebug     # ou: ./gradlew installDebug
```

No Windows: `.\gradlew.bat` no PowerShell. Ou direto pelo Android Studio (*Run ▸ app*).

> Na primeira execução o app copia os ~272 MiB do `map.mbtiles` para o armazenamento interno. Leva alguns segundos e acontece uma vez só.

### Preparando o mapa base

1. Acesse o [BBBike Extract](https://extract.bbbike.org/)
2. Selecione a região de Campinas
3. Escolha o formato **MBTiles (Shortbread)**
4. Renomeie o arquivo baixado para `map.mbtiles`
5. Coloque-o em `app/src/main/assets/map.mbtiles`

### Quando algo dá errado

| Sintoma | Causa provável |
|---|---|
| Mapa cinza, sem ruas | `map.mbtiles` ausente ou não é Shortbread |
| Traçado aparece, mapa não | `TileServer` não subiu — cheque a porta 7777 no logcat |
| Ruas sem nomes | glifos faltando em `assets/fonts/noto-sans/` |
| Linha some ao trocar para "Volta" | a linha é circular; o botão não deveria estar visível (terceiro campo da `Triple`) |
| Paradas numeradas fora de ordem | `OrdenadorDeParadas` bateu numa guarda e caiu na ordem do arquivo |
| Busca não acha a linha | ela não está em `linhasDisponiveis` — só os GeoJSON não bastam |

---

## Como adicionar uma nova linha

1. Colete os dados:
   ```bash
   python emdec_scraper.py 501
   ```

2. Copie os GeoJSON para `app/src/main/assets/routes/`, com os nomes exatos:
   ```
   501_sentido1_rota.geojson    501_sentido2_rota.geojson
   501_sentido1_pontos.geojson  501_sentido2_pontos.geojson
   ```

3. Crie os horários em `app/src/main/assets/horarios/501_sentido1.json` (e `_sentido2.json`):
   ```json
   { "linha": "501", "sentido": 1, "pontoPartida": "...", "horarios": ["05:30"] }
   ```

4. **Registre a linha** em `RouteRepository.kt` — sem este passo ela simplesmente não existe para a busca:
   ```kotlin
   "501" to Triple("Ponto de Ida", "Ponto de Volta", true),
   ```
   O terceiro campo só é `true` quando o GeoJSON realmente traz features com `"sentido": 2`. Marcar `true` numa circular deixa o botão "Volta" visível levando a um mapa vazio.

5. Recompile.

---

## Stack técnica

| Componente | Tecnologia | Versão |
|---|---|---|
| Linguagem | Kotlin (toolchain JVM 17) | — |
| Mapa | MapLibre Native Android | 11.8.4 |
| Servidor local | NanoHTTPD | 2.3.1 |
| Tiles | MBTiles · Shortbread (OSM) | — |
| Assíncrono | Kotlinx Coroutines · Lifecycle KTX | 1.9.0 · 2.8.7 |
| JSON | Gson · `org.json` | 2.11.0 · — |
| UI | AppCompat · Material 3 · ConstraintLayout · ViewBinding | 1.7.0 · 1.12.0 · 2.2.1 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 | — |

Sem injeção de dependência, sem banco, sem camada de rede: o app tem três colaboradores (`MainActivity`, `RouteRepository`, `TileServer`) e nenhum deles precisa de mais que isso.

Referências do TCC: arquitetura **offline-first**, documentação técnica em **C4** (níveis 1 a 3) e validação de usabilidade por **SUS (System Usability Scale)**.

---

## Estrutura do projeto

```
poc-tcc/
├── app/src/main/
│   ├── java/com/offbuss/
│   │   ├── MainActivity.kt              ← tela única: busca, mapa, diálogos
│   │   ├── model/Route.kt               ← data classes Route e Horarios
│   │   ├── data/
│   │   │   ├── RouteRepository.kt       ← índice de linhas, busca, leitura dos assets
│   │   │   └── OrdenadorDeParadas.kt    ← ordena as paradas pela geometria da rota
│   │   └── utils/
│   │       ├── TileServer.kt            ← servidor HTTP de tiles do MBTiles
│   │       └── MapStyleHelper.kt        ← cópia do .mbtiles e leitura do estilo
│   ├── assets/
│   │   ├── map.mbtiles                  ← tiles vetoriais (não versionado)
│   │   ├── map-style.json               ← estilo visual (schema Shortbread)
│   │   ├── corrigir-linhas.json         ← ida/volta oficiais da EMDEC
│   │   ├── fonts/noto-sans/             ← glifos dos rótulos
│   │   ├── routes/                      ← GeoJSON de traçados e paradas
│   │   └── horarios/                    ← JSON de horários por linha e sentido
│   └── res/layout/
│       ├── activity_main.xml            ← tela principal
│       ├── item_linha.xml               ← item da lista de resultados
│       └── dialog_horarios.xml          ← diálogo de horários
└── docs/                                ← contexto do TCC, tutorial de instalação
```

---

<sub>Feito em Campinas, para quem pega ônibus em Campinas.</sub>
