# OffBus — Consulta Offline de Ônibus em Campinas

> Trabalho de Conclusão de Curso · IFSP — Campus Campinas
> Discente: **Marcus Vinicius de Souza Gomes** · Orientadora: **Prof. Msa. Zady Castañeda Salazar**

Aplicativo Android nativo, escrito em Kotlin, que permite consultar **rotas, paradas e horários** dos ônibus municipais de Campinas/SP **sem nenhuma conexão com a internet**. Todos os dados operacionais ficam embutidos no APK e o mapa vetorial é servido localmente pelo próprio aplicativo.

---

## Problema e justificativa

A maioria das ferramentas de transporte público (Cittamobi, Moovit e similares) exige conectividade constante para fornecer informações básicas. No Brasil — em especial para a população de menor renda, dependente de planos pré-pagos — o fim da franquia de dados vira uma barreira concreta de **exclusão digital**: sem internet, não há como consultar um horário.

O OffBus responde a isso adotando o paradigma **offline-first**. Os dados de transporte de Campinas são pré-carregados no dispositivo, de modo que rotas e horários continuam acessíveis sem sinal de rede e sem franquia de dados.

---

## Funcionalidades

- Busca de linhas por **número**, **ponto inicial** ou **ponto final**
- Mapa vetorial offline de Campinas, com o traçado da rota desenhado sobre a malha de ruas
- Marcadores das paradas ao longo do trajeto
- Alternância entre **sentido 1 (ida)** e **sentido 2 (volta)**
- Tabela de horários de partida por linha e sentido
- Sem cadastro, sem login e sem uso de GPS

### Linhas disponíveis

| Linha | Ponto inicial | Ponto final |
|-------|---------------|-------------|
| 116 | Terminal Ouro Verde | Shopping Dom Pedro |
| 117 | Terminal Ouro Verde | Jardim Itatinga |
| 138 | Terminal Ouro Verde | Recanto do Sol I e II |

O índice de linhas fica em `linhasDisponiveis`, dentro de `RouteRepository.kt`.

---

## Arquitetura

O aplicativo se divide em três responsabilidades: **mapa base**, **dados de rota** e **dados de horário**. Nenhuma delas faz requisição para a internet em tempo de execução — o único tráfego HTTP é para `127.0.0.1`.

```
┌──────────────────────────────────────────────────────────┐
│                      MainActivity                         │
│   busca    → RouteRepository → GeoJSON → camadas MapLibre │
│   horários → RouteRepository → JSON    → AlertDialog      │
│   mapa     → TileServer (porta 7777)   ← MapLibre         │
└─────────────────────┬────────────────────────────────────┘
                      │
        ┌─────────────┴──────────────┐
        │                            │
  TileServer (NanoHTTPD)        assets/
  → TileArchive                 ├── map.tiles + map.idx
  lê map.tiles do próprio APK   ├── routes/*.geojson
  serve /tiles/{z}/{x}/{y}      ├── horarios/*.json
  serve /fonts/{stack}/{range}  └── map-style.json
```

### Sobre a permissão de INTERNET

O `AndroidManifest.xml` **declara** `android.permission.INTERNET`. Isso não contradiz a proposta offline: a permissão é exigida pelo Android para que o app consiga abrir o socket do servidor HTTP local. O `network_security_config.xml` restringe o tráfego em texto claro exclusivamente ao domínio `127.0.0.1`.

---

## Como o mapa offline funciona

Esta é a parte central do projeto: exibir o mapa completo de Campinas sem depender de nenhuma API externa.

### 1. O arquivo de tiles

O mapa base fica em dois assets, `map.tiles` e `map.idx`, com tiles vetoriais no schema [Shortbread](https://shortbread-tiles.org/) gerados a partir de dados do OpenStreetMap. Cada tile é um blob PBF (Protocol Buffer) endereçado por `(z, x, y)`.

A origem dos dados é um `.mbtiles` do BBBike, mas ele **não** entra no APK — é convertido antes do build por `tools/mbtiles_para_arquivo.py`. Nenhum dos três arquivos está versionado; veja [Preparando o mapa base](#preparando-o-mapa-base).

| Arquivo | Conteúdo |
|---------|----------|
| `map.tiles` | os blobs PBF concatenados, ainda em gzip |
| `map.idx` | índice de largura fixa, ordenado, com `(chave, offset, tamanho)` |

O índice tem um cabeçalho de 12 bytes (`"OFFB"`, versão, contagem) seguido de entradas de 16 bytes, todas little-endian:

```
chave u64  = (z << 58) | (x << 29) | y     // ordenável, cobre até o zoom 29
offset u32 = posição do blob em map.tiles
tamanho u32
```

### 2. Por que o mapa não é mais copiado para o `filesDir`

Até a versão 1.0 o mapa era um `.mbtiles` — um banco SQLite — e o `SQLiteDatabase` do Android só abre um **caminho no sistema de arquivos**, nunca um asset. O app contornava isso copiando os ~272 MiB dos assets para o armazenamento interno na primeira execução. O custo era alto: a primeira abertura demorava dezenas de segundos e o app passava a ocupar o dobro de espaço no aparelho.

A limitação era do SQLite, não do Android. Como o asset é gravado **sem compressão**, ele é uma região contígua dentro do próprio APK, e `AssetManager.openFd` devolve o descritor do APK junto do deslocamento onde o asset começa. Trocando o container SQLite por um arquivo plano com índice, ler um tile vira uma busca binária em memória mais um `read` posicional — sem cópia nenhuma:

```kotlin
// TileArchive.kt
private val canal = FileInputStream(descritor.fileDescriptor).channel
// read posicional: não move o cursor, então várias threads leem ao mesmo tempo
canal.read(buffer, descritor.startOffset + offset)
```

Por isso as extensões `tiles` e `idx` **precisam** estar em `noCompress` — não como otimização, mas como requisito: com o asset comprimido, `openFd` falha e o mapa não abre.

```kotlin
androidResources {
    noCompress += listOf("tiles", "idx", "geojson")
}
```

O `MapStyleHelper.limparCopiaAntiga` apaga o `map.mbtiles` que ficou no `filesDir` de quem atualizou por cima da versão antiga.

### 3. O servidor local de tiles

O MapLibre espera receber tiles por HTTP. O `TileServer` implementa um micro-servidor com [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) em `127.0.0.1:7777`, servindo o que o `TileArchive` lê do APK.

```
MapLibre pede  → http://127.0.0.1:7777/tiles/14/9298/5671
TileServer     → TileArchive: busca binária no índice
               → lê o blob por offset, direto do APK
               → descomprime gzip, se necessário
               → devolve PBF puro
```

**Inversão de coordenadas TMS ↔ XYZ.** O MBTiles guarda tiles no esquema TMS, cujo eixo Y é invertido em relação ao XYZ usado pelo MapLibre. A inversão passou a ser feita uma única vez, na conversão, em vez de a cada requisição:

```python
# mbtiles_para_arquivo.py
y = (1 << z) - 1 - linha_tms
```

**Descompressão.** Os tiles costumam estar em gzip dentro do banco, mas o MapLibre espera PBF puro. A detecção é feita pelo *magic number*:

```kotlin
if (data.size >= 2 && data[0] == 0x1F.toByte() && data[1] == 0x8B.toByte()) {
    data = GZIPInputStream(data.inputStream()).use { it.readBytes() }
}
```

**Fontes (glyphs).** O mesmo servidor entrega os glifos dos rótulos, lidos direto dos assets (`/fonts/noto-sans/0-255.pbf` → `assets/fonts/noto-sans/0-255.pbf`). Quando uma fonte não existe, a resposta é **200 com corpo vazio, e não 404** — um 404 faria o MapLibre invalidar o estilo em cascata.

### 4. O estilo (`map-style.json`)

Define as camadas visuais seguindo a MapLibre Style Spec e aponta para o servidor local:

```json
{
  "sources": {
    "mbtiles-source": {
      "type": "vector",
      "tiles": ["http://127.0.0.1:7777/tiles/{z}/{x}/{y}"],
      "minzoom": 0,
      "maxzoom": 14
    }
  },
  "glyphs": "http://127.0.0.1:7777/fonts/{fontstack}/{range}.pbf"
}
```

As camadas consomem os `source-layer` do schema Shortbread: `water_polygons`, `land`, `street_polygons`, `streets`, `buildings`, `place_labels` e `street_labels`.

### 5. Fluxo de inicialização

```
onCreate()
  └─ getMapAsync { ... }                      // em Dispatchers.IO
       ├─ MapStyleHelper.limparCopiaAntiga()    // remove o .mbtiles de versões antigas
       ├─ MapStyleHelper.prepareOfflineStyle()  // lê o map-style.json
       ├─ TileServer.openAndStart()             // abre o índice, sobe o HTTP :7777
       └─ map.setStyle(...)                     // (Main) carrega o estilo
            └─ mapReady = true                  // e renderiza rota pendente, se houver
```

Não há mais cópia de arquivo nesse caminho: abrir o mapa é ler o índice e um descritor, na casa dos milissegundos. Enquanto o estilo carrega, um `ProgressBar` fica sobre a área do mapa — antes a rota esperava em `pendingRota` sem nada na tela.

Se os assets do mapa faltarem ou tiverem sido comprimidos no empacotamento, `TileArchive.abrir` lança `IOException` e a `MainActivity` avisa por `Toast` em vez de deixar a tela vazia.

O servidor é encerrado em `onDestroy()` via `tileServer?.stopServer()`.

---

## Rotas e paradas

Os dados de rota vêm de GeoJSON em `assets/routes/`, dois arquivos por sentido:

```
116_sentido1_rota.geojson    ← geometria do traçado (LineString ou MultiLineString)
116_sentido1_pontos.geojson  ← paradas (FeatureCollection de Points)
```

As linhas 116 e 117 usam `LineString`; a 138 usa `MultiLineString`. As propriedades vêm da EMDEC (`num_linha`, `sentido`, `nome_linha`, `extensao_m`, entre outras).

A `MainActivity` adiciona duas camadas ao estilo:

```kotlin
LineLayer("route-layer", "route-source").withProperties(
    PropertyFactory.lineColor("#2196F3"),
    PropertyFactory.lineWidth(5f),
    PropertyFactory.lineCap("round"),
    PropertyFactory.lineJoin("round")
)

CircleLayer("stops-layer", "stops-source").withProperties(
    PropertyFactory.circleColor("#2196F3"),
    PropertyFactory.circleRadius(6f),
    PropertyFactory.circleStrokeColor("#FFFFFF"),
    PropertyFactory.circleStrokeWidth(2f)
)
```

Nas trocas seguintes de linha ou sentido, as camadas não são recriadas: apenas o conteúdo das *sources* é substituído com `setGeoJson`. Em seguida, `enquadrarMapa` calcula o *bounding box* do traçado e ajusta a câmera com 80 px de margem.

---

## Horários

Ficam em `assets/horarios/`, um arquivo por linha e sentido:

```json
{
  "linha": "116",
  "sentido": 1,
  "pontoPartida": "Terminal Ouro Verde",
  "horarios": ["05:10", "05:40", "06:10", "06:40"]
}
```

O `RouteRepository` desserializa com Gson na data class `Horarios` (`model/Route.kt`). Quando o arquivo não existe, o diálogo exibe "Horários não cadastrados" em vez de falhar.

---

## Pipeline de coleta de dados

Os GeoJSON são extraídos do portal público da [EMDEC](https://portal.emdec.com.br) pelo script `emdec_scraper.py`:

```
emdec_scraper.py
  ├─ GET /itinerarios/?linha=116&sentido=1
  │    └─ localiza os <script src> com o GeoJSON embutido
  ├─ lin_itinerarios…js  →  116_sentido1_rota.geojson
  └─ pto_itinerarios…js  →  116_sentido1_pontos.geojson
```

```bash
pip install requests beautifulsoup4
python emdec_scraper.py 116 117 138
```

> O script **não faz parte deste repositório** e grava em um diretório fixo definido na constante `OUTPUT_DIR`. A cópia dos arquivos gerados para `app/src/main/assets/routes/` é **manual**.

---

## Preparando o mapa base

O `map.mbtiles` não está no repositório por causa do tamanho. Para obtê-lo:

1. Acesse o [BBBike Extract](https://extract.bbbike.org/)
2. Selecione a região de Campinas
3. Escolha o formato **MBTiles (Shortbread)**
4. Renomeie o arquivo baixado para `map.mbtiles`
5. Converta para os assets que o app lê (o `.mbtiles` em si **não** vai para dentro do APK):

```bash
python3 tools/mbtiles_para_arquivo.py map.mbtiles app/src/main/assets
```

O script só usa a biblioteca padrão do Python 3. Ele inverte TMS→XYZ, deduplica blobs idênticos (tiles de área vazia repetem bastante) e grava `map.tiles` + `map.idx` no destino.

### Encolhendo o mapa base

O extract do BBBike cobre um retângulo bem maior do que as linhas de ônibus percorrem, e o peso do APK vem quase todo dele. Como o conversor reescreve o arquivo tile a tile, dá para recortar na mesma passada:

```bash
# bbox que cobre todas as rotas de assets/routes/, com 2 km de folga
python3 tools/bbox_das_rotas.py

python3 tools/mbtiles_para_arquivo.py map.mbtiles app/src/main/assets \
  --bbox $(python3 tools/bbox_das_rotas.py --argumentos) \
  --max-zoom 14
```

O `--max-zoom 14` acompanha o `"maxzoom": 14` declarado na source do `map-style.json`: zooms acima disso ocupam espaço e nunca são pedidos (o MapLibre faz *overzoom* a partir do z14).

O bbox das rotas dá cerca de 40 × 40 km — algo como 18 × 18 tiles no zoom 14. Vale comparar a saída do script com o tamanho do `.mbtiles` original antes de decidir se o recorte foi longe o bastante.

---

## Build e execução

**Requisitos:** Android Studio · JDK 17 · Python 3 (só para preparar o mapa) · dispositivo ou emulador com Android 8.0 (API 26) ou superior.

```bash
git clone https://github.com/markinog/poc-tcc.git
cd poc-tcc

# gere map.tiles e map.idx em app/src/main/assets/ (veja a seção acima)

./gradlew testDebugUnitTest  # verifica o leitor do mapa contra a fixture
./gradlew assembleDebug      # ou: ./gradlew installDebug
```

No Windows, use `.\gradlew.bat` no PowerShell. O projeto também roda direto pelo Android Studio (*Run > Run 'app'*).

> A primeira abertura não faz cópia de arquivo nenhuma: o mapa é lido de dentro do APK. No aparelho o app ocupa só o tamanho do arquivo instalado.

---

## Stack técnica

| Componente | Tecnologia | Versão |
|------------|-----------|--------|
| Linguagem | Kotlin (toolchain JVM 17) | — |
| Mapa | MapLibre Native Android | 11.8.4 |
| Servidor local | NanoHTTPD | 2.3.1 |
| Tiles | MBTiles · Shortbread (OSM) | — |
| Assíncrono | Kotlinx Coroutines | 1.9.0 |
| JSON | Gson | 2.11.0 |
| UI | AppCompat · Material 3 · ViewBinding | 1.7.0 / 1.12.0 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 | — |
| Build | AGP · Gradle | 9.0.1 · 9.2.1 |

Arquitetura de referência do TCC: **offline-first**, com documentação técnica modelada em **C4** (níveis 1 a 3) e validação de usabilidade por questionário **SUS (System Usability Scale)**.

---

## Como adicionar uma nova linha

1. Colete os dados:
   ```bash
   python emdec_scraper.py 101
   ```

2. Copie os GeoJSON para `app/src/main/assets/routes/`, respeitando exatamente o padrão de nome:
   ```
   101_sentido1_rota.geojson    101_sentido2_rota.geojson
   101_sentido1_pontos.geojson  101_sentido2_pontos.geojson
   ```

3. Crie os horários em `app/src/main/assets/horarios/101_sentido1.json` (e `_sentido2.json`):
   ```json
   { "linha": "101", "sentido": 1, "pontoPartida": "...", "horarios": ["05:30"] }
   ```

4. **Registre a linha** em `RouteRepository.kt` — sem este passo ela não aparece na busca:
   ```kotlin
   private val linhasDisponiveis = mapOf(
       "101" to Triple("Ponto Inicial", "Ponto Final", true),
       // ...
   )
   ```
   O terceiro elemento da `Triple` indica se a linha possui sentido 2.

5. Recompile e execute.

---

## Estrutura do projeto

```
poc-tcc/
├── app/src/main/
│   ├── java/com/offbuss/
│   │   ├── MainActivity.kt          ← tela principal, busca e controle do mapa
│   │   ├── model/Route.kt           ← data classes Route e Horarios
│   │   ├── data/RouteRepository.kt  ← índice de linhas e leitura dos assets
│   │   └── utils/
│   │       ├── TileServer.kt        ← servidor HTTP de tiles em 127.0.0.1:7777
│   │       ├── TileArchive.kt       ← lê os tiles por offset, direto do APK
│   │       └── MapStyleHelper.kt    ← leitura do estilo e limpeza da cópia antiga
│   ├── assets/
│   │   ├── map.tiles + map.idx      ← tiles vetoriais (não versionados)
│   │   ├── map-style.json           ← estilo visual (schema Shortbread)
│   │   ├── fonts/noto-sans/         ← glifos dos rótulos
│   │   ├── routes/                  ← GeoJSON de traçados e paradas
│   │   └── horarios/                ← JSON de horários por linha e sentido
│   └── res/layout/
│       ├── activity_main.xml        ← tela principal
│       └── dialog_horarios.xml      ← diálogo de horários
├── app/src/test/                    ← testes JVM do TileArchive + fixture do mapa
├── tools/
│   ├── mbtiles_para_arquivo.py      ← converte o .mbtiles em map.tiles + map.idx
│   └── bbox_das_rotas.py            ← bbox das rotas, para recortar o mapa base
└── docs/contexto-do-tcc.md          ← contexto acadêmico do TCC
```
