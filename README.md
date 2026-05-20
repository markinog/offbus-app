# BusApp — Consulta Offline de Ônibus em Campinas

> Trabalho de Conclusão de Curso · IFSP Campus Campinas  
> Curso de Análise e Desenvolvimento de Sistemas

Aplicativo Android nativo em Kotlin que permite consultar **rotas, paradas e horários** de ônibus municipais de Campinas **sem nenhuma conexão com a internet**. Todos os dados ficam embutidos no APK e o mapa vetorial é servido localmente pelo próprio app.

---

## Funcionalidades

- Busca de linhas por número, ponto inicial ou ponto final
- Mapa vetorial offline com rota desenhada (linha azul)
- Marcadores de paradas ao longo do trajeto
- Alternância entre sentido 1 (ida) e sentido 2 (volta)
- Horários de partida consultáveis por linha e sentido
- Funciona sem internet e sem GPS (somente visualização de mapa)

**Linhas disponíveis**

| Linha | Origem | Destino |
|-------|--------|---------|
| 116 | Terminal Ouro Verde | Shopping Dom Pedro |
| 117 | Terminal Ouro Verde | Jardim Itatinga |
| 138 | Terminal Ouro Verde | Recanto do Sol I e II |

---

## Arquitetura

O app é dividido em três responsabilidades principais: **dados de mapa**, **dados de rota** e **dados de horários**. Tudo roda localmente — não há nenhuma permissão de internet declarada no `AndroidManifest.xml`.

```
┌─────────────────────────────────────────────────────────┐
│                      MainActivity                        │
│  busca → RouteRepository → GeoJSON → MapLibre overlay   │
│  horários → RouteRepository → JSON → AlertDialog        │
│  mapa → TileServer (porta 7777) ← MapLibre estilo       │
└────────────────────┬────────────────────────────────────┘
                     │
       ┌─────────────┴──────────────┐
       │                            │
  TileServer (NanoHTTPD)     assets/
  abre map.mbtiles (SQLite)  ├── routes/*.geojson
  serve /tiles/{z}/{x}/{y}   ├── horarios/*.json
  serve /fonts/...            └── map-style.json
```

---

## Como o mapa offline funciona

Esta é a parte central do projeto. O desafio é exibir um mapa completo de Campinas sem depender de nenhuma API externa.

### 1. O arquivo `.mbtiles`

O mapa base é armazenado em `assets/map.mbtiles`, um **banco de dados SQLite** que contém tiles vetoriais no formato [Shortbread](https://shortbread-tiles.org/) gerados pelo [BBBike](https://extract.bbbike.org/) a partir de dados do OpenStreetMap.

Cada tile é um blob PBF (Protocol Buffer), identificado por coordenadas `(zoom, x, y)` no esquema TMS. O arquivo possui a seguinte estrutura:

```sql
-- Tabela principal de tiles
SELECT tile_data FROM tiles
WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?
```

> O arquivo tem ~272 MB e **não está incluído no repositório** (limite do GitHub).  
> Veja a seção [Preparando o mapa base](#preparando-o-mapa-base) para gerá-lo.

### 2. Problema: SQLite não lê direto dos assets

O Android compacta os assets dentro do APK. O SQLite não consegue abrir um arquivo compactado em posição arbitrária no ZIP. A solução adotada foi:

**No primeiro uso**, o `MapStyleHelper.kt` copia o `map.mbtiles` dos assets para o `filesDir` do app (armazenamento interno do dispositivo):

```kotlin
// MapStyleHelper.kt
private fun copyMbtilesToFilesDir(context: Context) {
    val dest = File(context.filesDir, "map.mbtiles")
    if (!dest.exists()) {
        context.assets.open("map.mbtiles").use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
```

O `build.gradle.kts` instrui o AGP a **não comprimir** arquivos `.mbtiles` e `.geojson`, o que acelera a cópia inicial e evita problemas de acesso aleatório:

```kotlin
androidResources {
    noCompress += listOf("mbtiles", "geojson")
}
```

### 3. Servidor HTTP local (TileServer)

O MapLibre espera receber tiles via HTTP. O `TileServer.kt` implementa um micro-servidor HTTP usando [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd), rodando em `127.0.0.1:7777`.

```
MapLibre solicita → http://127.0.0.1:7777/tiles/14/9298/5671
TileServer recebe → z=14, x=9298, y=5671
                  → converte Y: tmsY = (1 << 14) - 1 - 5671
                  → consulta SQLite
                  → descomprime gzip → retorna PBF puro
```

**Conversão de coordenadas TMS ↔ XYZ:**  
O MBTiles armazena tiles no sistema TMS, onde o eixo Y é invertido em relação ao padrão XYZ do MapLibre. A conversão é:

```kotlin
val tmsY = (1 shl z) - 1 - y
```

**Descompressão de tiles:**  
Os tiles vêm comprimidos em gzip dentro do banco. O MapLibre espera PBF puro:

```kotlin
if (data[0] == 0x1F.toByte() && data[1] == 0x8B.toByte()) {
    data = GZIPInputStream(data.inputStream()).use { it.readBytes() }
}
```

O servidor também serve fontes (glyphs) usadas nos rótulos do mapa, lendo-as diretamente dos assets:

```
GET /fonts/noto-sans/0-255.pbf  →  assets/fonts/noto-sans/0-255.pbf
```

### 4. Estilo do mapa (`map-style.json`)

O `map-style.json` define as camadas visuais (água, ruas, edifícios, rótulos) usando a especificação MapLibre Style Spec. Ele aponta para o servidor local:

```json
{
  "sources": {
    "mbtiles-source": {
      "type": "vector",
      "tiles": ["http://127.0.0.1:7777/tiles/{z}/{x}/{y}"]
    }
  },
  "glyphs": "http://127.0.0.1:7777/fonts/{fontstack}/{range}.pbf"
}
```

Os nomes das camadas (`source-layer`) seguem o schema **Shortbread**: `water_polygons`, `land`, `streets`, `buildings`, `place_labels`, `street_labels`.

### 5. Fluxo completo de inicialização

```
onCreate()
  │
  ├─ MapStyleHelper.copyMbtilesToFilesDir()   // copia .mbtiles (só 1ª vez)
  ├─ TileServer.openAndStart()               // abre SQLite, HTTP :7777
  ├─ MapStyleHelper.prepareOfflineStyle()    // lê map-style.json
  └─ MapLibre.setStyle(styleJson)            // carrega estilo → solicita tiles
                                             // → TileServer responde com PBF
```

---

## Como as rotas são exibidas

Os dados de rota vêm de arquivos GeoJSON armazenados em `assets/routes/`. Cada linha tem dois pares de arquivos (um por sentido):

```
116_sentido1_rota.geojson    ← geometria da rota (LineString / MultiLineString)
116_sentido1_pontos.geojson  ← paradas (FeatureCollection de Points)
```

O `RouteRepository.kt` lê esses arquivos e a `MainActivity` adiciona duas camadas ao estilo do MapLibre:

```kotlin
// Linha da rota
LineLayer("route-layer", "route-source").withProperties(
    PropertyFactory.lineColor("#2196F3"),
    PropertyFactory.lineWidth(5f),
    PropertyFactory.lineCap("round"),
    PropertyFactory.lineJoin("round")
)

// Marcadores de parada
CircleLayer("stops-layer", "stops-source").withProperties(
    PropertyFactory.circleColor("#2196F3"),
    PropertyFactory.circleRadius(6f),
    PropertyFactory.circleStrokeColor("#FFFFFF"),
    PropertyFactory.circleStrokeWidth(2f)
)
```

Ao carregar uma rota, a câmera é automaticamente enquadrada para exibir o trajeto completo, calculando o bounding box a partir das coordenadas do GeoJSON e aplicando 80 px de margem.

---

## Como os horários são armazenados

Os horários ficam em `assets/horarios/`, um arquivo JSON por linha por sentido:

```json
{
  "linha": "116",
  "sentido": 1,
  "pontoPartida": "Terminal Ouro Verde",
  "horarios": ["05:10", "05:40", "06:10", ...]
}
```

O `RouteRepository.kt` desserializa esses arquivos com Gson na classe `Horarios`:

```kotlin
data class Horarios(
    val linha: String,
    val sentido: Int,
    val pontoPartida: String,
    val horarios: List<String>
)
```

---

## Pipeline de coleta de dados

Os dados de rota são coletados da plataforma da [EMDEC](http://www.emdec.com.br) via o script `emdec_scraper.py`:

```
emdec_scraper.py
  │
  ├─ GET /itinerarios/?linha=116&sentido=1
  │   └─ extrai URLs dos arquivos .js com GeoJSON embutido
  ├─ GET lin_itinerarios_116_1.js  →  salva 116_sentido1_rota.geojson
  └─ GET pto_itinerarios_116_1.js  →  salva 116_sentido1_pontos.geojson
```

**Uso:**

```bash
pip install requests beautifulsoup4
python emdec_scraper.py 116 117 138
```

Os arquivos gerados são copiados manualmente para `app/src/main/assets/routes/`.

---

## Preparando o mapa base

O arquivo `map.mbtiles` não está no repositório por ser muito grande (272 MB). Para gerá-lo:

1. Acesse [BBBike Extract](https://extract.bbbike.org/)
2. Selecione a região de Campinas (ou importe o polígono desejado)
3. Escolha o formato **Protomaps (MBTiles, Shortbread)**
4. Baixe o arquivo `.mbtiles` gerado e renomeie para `map.mbtiles`
5. Coloque em `app/src/main/assets/map.mbtiles`

---

## Stack técnica

| Componente | Tecnologia | Versão |
|------------|-----------|--------|
| Linguagem | Kotlin | 2.0.21 |
| Mapa | MapLibre Native Android | 11.8.4 |
| Servidor local | NanoHTTPD | 2.3.1 |
| Tiles | MBTiles / Shortbread (OSM) | — |
| Assíncrono | Kotlinx Coroutines | 1.9.0 |
| JSON | Gson | 2.11.0 |
| compileSdk / minSdk | Android 35 / 26 (8.0+) | — |
| Build | AGP | 9.0.1 |

---

## Estrutura do projeto

```
poc-tcc/
├── app/src/main/
│   ├── java/com/offbuss/
│   │   ├── MainActivity.kt          ← tela principal e controle do mapa
│   │   ├── model/Route.kt           ← data classes Route e Horarios
│   │   ├── data/RouteRepository.kt  ← lê assets (GeoJSON, horários)
│   │   └── utils/
│   │       ├── TileServer.kt        ← HTTP server para tiles MBTiles
│   │       └── MapStyleHelper.kt    ← copia mbtiles, carrega estilo
│   ├── assets/
│   │   ├── map.mbtiles              ← banco SQLite de tiles (272 MB, não versionado)
│   │   ├── map-style.json           ← estilo visual do mapa (Shortbread)
│   │   ├── fonts/noto-sans/         ← glyphs para rótulos do mapa
│   │   ├── routes/                  ← GeoJSON de rotas e paradas
│   │   └── horarios/                ← JSON de horários por linha e sentido
│   └── res/layout/
│       ├── activity_main.xml        ← tela principal
│       └── dialog_horarios.xml      ← popup de horários
└── emdec_scraper.py                 ← coleta GeoJSON da EMDEC
```

---

## Como adicionar uma nova linha

1. Colete os dados:
   ```bash
   python emdec_scraper.py 101
   ```

2. Copie os arquivos para assets:
   ```
   assets/routes/101_sentido1_rota.geojson
   assets/routes/101_sentido1_pontos.geojson
   assets/routes/101_sentido2_rota.geojson
   assets/routes/101_sentido2_pontos.geojson
   ```

3. Crie os arquivos de horários em `assets/horarios/`:
   ```json
   { "linha": "101", "sentido": 1, "pontoPartida": "...", "horarios": ["05:30", ...] }
   ```

4. Registre a linha em `RouteRepository.kt`:
   ```kotlin
   private val linhasDisponiveis = mapOf(
       "101" to Triple("Ponto Inicial", "Ponto Final", true),
       // ...
   )
   ```

5. Rebuild e execute o app.

---

## Build e execução

**Requisitos:** Android Studio Hedgehog+ · JDK 17 · dispositivo ou emulador Android 8.0+

```bash
# Clone o repositório
git clone https://github.com/markinog/poc-tcc.git
cd poc-tcc

# Adicione o map.mbtiles em app/src/main/assets/ (veja instruções acima)

# Abra no Android Studio e execute (Run > Run 'app')
```

> Na primeira execução, o app copia os 272 MB do `map.mbtiles` para o armazenamento interno do dispositivo. Isso pode levar alguns segundos dependendo do hardware.
