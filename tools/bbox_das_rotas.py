#!/usr/bin/env python3
"""Calcula o bbox que cobre todas as rotas em assets/routes/.

O extract do BBBike vem com bem mais área do que o app usa: o mapa só precisa
alcançar onde as linhas passam. A saída daqui alimenta o `--bbox` de
`mbtiles_para_arquivo.py`, que descarta os tiles fora da região.

    python3 tools/bbox_das_rotas.py
    python3 tools/mbtiles_para_arquivo.py map.mbtiles app/src/main/assets \\
        --bbox $(python3 tools/bbox_das_rotas.py --argumentos)

A margem existe porque o traçado é a linha do ônibus, não o enquadramento do
mapa: sem folga, arrastar o dedo para o lado do trajeto cairia no vazio.
"""

import argparse
import glob
import json
import math
import os
import sys

RAIO_DA_TERRA_KM = 6371.0


def coordenadas(no):
    """Percorre qualquer geometria GeoJSON e devolve os pares (lon, lat)."""
    if isinstance(no, (list, tuple)):
        # Um par de números é uma posição; qualquer outra lista é aninhamento
        if len(no) >= 2 and all(isinstance(v, (int, float)) for v in no[:2]):
            yield no[0], no[1]
        else:
            for item in no:
                yield from coordenadas(item)


def bbox_dos_arquivos(caminhos):
    oeste = sul = leste = norte = None

    for caminho in caminhos:
        with open(caminho, encoding="utf-8") as f:
            try:
                dados = json.load(f)
            except json.JSONDecodeError as e:
                print(f"aviso: {os.path.basename(caminho)} ilegível ({e})",
                      file=sys.stderr)
                continue

        for feature in dados.get("features", []):
            geometria = feature.get("geometry") or {}
            for lon, lat in coordenadas(geometria.get("coordinates")):
                oeste = lon if oeste is None else min(oeste, lon)
                leste = lon if leste is None else max(leste, lon)
                sul = lat if sul is None else min(sul, lat)
                norte = lat if norte is None else max(norte, lat)

    if oeste is None:
        raise SystemExit("Nenhuma coordenada encontrada nos GeoJSON.")
    return oeste, sul, leste, norte


def com_margem(bbox, km):
    oeste, sul, leste, norte = bbox
    graus_lat = math.degrees(km / RAIO_DA_TERRA_KM)
    # Um grau de longitude encolhe conforme se afasta do equador
    latitude_media = math.radians((sul + norte) / 2)
    graus_lon = graus_lat / max(math.cos(latitude_media), 1e-6)
    return (oeste - graus_lon, sul - graus_lat, leste + graus_lon, norte + graus_lat)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--rotas", default="app/src/main/assets/routes",
                        help="diretório com os .geojson (padrão: %(default)s)")
    parser.add_argument("--margem-km", type=float, default=2.0)
    parser.add_argument("--argumentos", action="store_true",
                        help="imprime só os quatro números, para usar em --bbox")
    args = parser.parse_args(argv)

    caminhos = sorted(glob.glob(os.path.join(args.rotas, "*.geojson")))
    if not caminhos:
        raise SystemExit(f"Nenhum .geojson em {args.rotas}")

    bbox = com_margem(bbox_dos_arquivos(caminhos), args.margem_km)

    if args.argumentos:
        print(" ".join(f"{v:.6f}" for v in bbox))
        return

    oeste, sul, leste, norte = bbox
    print(f"arquivos lidos: {len(caminhos)}")
    print(f"margem:         {args.margem_km} km")
    print(f"bbox:           {oeste:.6f} {sul:.6f} {leste:.6f} {norte:.6f}")
    print(f"                (oeste sul leste norte)")


if __name__ == "__main__":
    sys.exit(main())
