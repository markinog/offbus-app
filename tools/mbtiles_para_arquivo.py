#!/usr/bin/env python3
"""Converte o map.mbtiles no par map.tiles + map.idx lido direto do APK.

O SQLite não abre um arquivo de dentro do APK: só aceita um caminho no sistema
de arquivos. Era por isso que o app copiava os 272 MiB dos assets para o
filesDir na primeira execução — uma espera longa e o dobro de espaço ocupado.

O formato gerado aqui troca o container SQLite por um arquivo plano com índice
de largura fixa, que o TileArchive lê por offset direto do APK. Nada é copiado
em runtime.

    map.tiles  blobs PBF concatenados, ainda em gzip (como vêm do MBTiles)
    map.idx    cabeçalho + entradas de 16 bytes ordenadas por chave

    cabeçalho  magic "OFFB" (4B) | versão u8 | reservado 3B | entradas u32
    entrada    chave u64 | offset u32 | tamanho u32          (little-endian)
    chave      (z << 58) | (x << 29) | y  — ordenável, cobre até o zoom 29

Uso:
    python3 tools/mbtiles_para_arquivo.py ENTRADA.mbtiles DIRETORIO_SAIDA
        [--bbox OESTE SUL LESTE NORTE] [--min-zoom N] [--max-zoom N]

Sem --bbox e sem limites de zoom, a conversão é fiel: todo tile do MBTiles vai
para o arquivo. Os recortes são o mecanismo para encolher o mapa base, que vem
do BBBike com mais área e mais zooms do que o app usa.
"""

import argparse
import hashlib
import math
import os
import sqlite3
import struct
import sys

MAGIC = b"OFFB"
VERSAO = 1
TAMANHO_CABECALHO = 12
TAMANHO_ENTRADA = 16

# O offset é u32: o arquivo de tiles precisa caber em 4 GiB.
LIMITE_ARQUIVO = 0xFFFFFFFF


def chave(z, x, y):
    """Empacota z/x/y num u64 cuja ordem numérica agrupa por zoom.

    O TileArchive faz busca binária sobre essas chaves, então a ordenação
    aqui e a comparação lá precisam ser a mesma — por isso a chave é um
    inteiro único em vez de uma tupla.
    """
    return (z << 58) | (x << 29) | y


def faixa_de_tiles(z, oeste, sul, leste, norte):
    """Intervalo [x0, x1] × [y0, y1] em XYZ que cobre o bbox no zoom z."""
    n = 1 << z

    def para_x(lon):
        return int((lon + 180.0) / 360.0 * n)

    def para_y(lat):
        # Web Mercator satura perto dos polos; o clamp evita math domain error
        lat = max(-85.0511, min(85.0511, lat))
        rad = math.radians(lat)
        return int((1.0 - math.asinh(math.tan(rad)) / math.pi) / 2.0 * n)

    x0 = max(0, para_x(oeste))
    x1 = min(n - 1, para_x(leste))
    # Y cresce para o sul: o norte do bbox é o menor y
    y0 = max(0, para_y(norte))
    y1 = min(n - 1, para_y(sul))
    return x0, x1, y0, y1


def converter(caminho_mbtiles, dir_saida, bbox=None, min_zoom=None, max_zoom=None):
    conexao = sqlite3.connect(f"file:{caminho_mbtiles}?mode=ro", uri=True)
    cursor = conexao.cursor()

    condicoes = []
    parametros = []
    if min_zoom is not None:
        condicoes.append("zoom_level >= ?")
        parametros.append(min_zoom)
    if max_zoom is not None:
        condicoes.append("zoom_level <= ?")
        parametros.append(max_zoom)

    consulta = "SELECT zoom_level, tile_column, tile_row, tile_data FROM tiles"
    if condicoes:
        consulta += " WHERE " + " AND ".join(condicoes)

    caminho_tiles = os.path.join(dir_saida, "map.tiles")
    caminho_idx = os.path.join(dir_saida, "map.idx")
    os.makedirs(dir_saida, exist_ok=True)

    entradas = []
    # Tiles idênticos (oceano, áreas vazias) repetem muito; guardar um só e
    # apontar várias entradas para ele encolhe o arquivo sem custo de leitura.
    por_conteudo = {}
    faixas = {}
    posicao = 0
    lidos = 0
    fora_do_bbox = 0

    with open(caminho_tiles, "wb") as saida:
        for z, coluna, linha_tms, dados in cursor.execute(consulta, parametros):
            lidos += 1
            if dados is None:
                continue

            # MBTiles guarda em TMS, com o eixo Y invertido em relação ao XYZ
            # que o MapLibre pede. A inversão passa a ser feita aqui, uma vez
            # na conversão, em vez de a cada requisição no TileServer.
            y = (1 << z) - 1 - linha_tms
            x = coluna

            if bbox is not None:
                if z not in faixas:
                    faixas[z] = faixa_de_tiles(z, *bbox)
                x0, x1, y0, y1 = faixas[z]
                if not (x0 <= x <= x1 and y0 <= y <= y1):
                    fora_do_bbox += 1
                    continue

            digest = hashlib.sha256(dados).digest()
            existente = por_conteudo.get(digest)
            if existente is None:
                if posicao + len(dados) > LIMITE_ARQUIVO:
                    raise SystemExit(
                        "map.tiles passaria de 4 GiB, que é o limite do offset "
                        "u32 do índice. Recorte por --bbox ou --max-zoom."
                    )
                saida.write(dados)
                existente = (posicao, len(dados))
                por_conteudo[digest] = existente
                posicao += len(dados)

            offset, tamanho = existente
            entradas.append((chave(z, x, y), offset, tamanho))

    conexao.close()

    if not entradas:
        raise SystemExit("Nenhum tile selecionado — confira o bbox e os zooms.")

    # A busca binária do TileArchive depende desta ordenação
    entradas.sort(key=lambda e: e[0])

    chaves = {e[0] for e in entradas}
    if len(chaves) != len(entradas):
        raise SystemExit(
            "Chaves z/x/y repetidas no MBTiles — o índice ficaria ambíguo."
        )

    with open(caminho_idx, "wb") as saida:
        saida.write(struct.pack("<4sB3xI", MAGIC, VERSAO, len(entradas)))
        empacotar = struct.Struct("<QII").pack
        for chave_tile, offset, tamanho in entradas:
            saida.write(empacotar(chave_tile, offset, tamanho))

    zooms = sorted({e[0] >> 58 for e in entradas})
    print(f"tiles lidos:      {lidos}")
    if bbox is not None:
        print(f"fora do bbox:     {fora_do_bbox}")
    print(f"tiles gravados:   {len(entradas)}")
    print(f"blobs únicos:     {len(por_conteudo)} (dedup economizou "
          f"{len(entradas) - len(por_conteudo)} cópias)")
    print(f"zooms:            {zooms[0]}–{zooms[-1]}")
    print(f"map.tiles:        {posicao / 1024 / 1024:.1f} MiB")
    print(f"map.idx:          {os.path.getsize(caminho_idx) / 1024 / 1024:.1f} MiB")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("mbtiles", help="arquivo .mbtiles de entrada")
    parser.add_argument("saida", help="diretório onde gravar map.tiles e map.idx")
    parser.add_argument("--bbox", nargs=4, type=float, metavar=("O", "S", "L", "N"),
                        help="recorta pelos limites oeste sul leste norte")
    parser.add_argument("--min-zoom", type=int)
    parser.add_argument("--max-zoom", type=int)
    args = parser.parse_args(argv)

    if not os.path.exists(args.mbtiles):
        raise SystemExit(f"Não encontrei {args.mbtiles}")

    converter(args.mbtiles, args.saida, args.bbox, args.min_zoom, args.max_zoom)


if __name__ == "__main__":
    sys.exit(main())
