#!/usr/bin/env python3
"""
import_to_db.py — graph.json / GeoJSON / 영상 메타데이터를 DB에 적재

실행: python scripts/import_to_db.py   (SKKU_navigation_backend/ 에서)

적재 대상:
  1. nav_nodes  — graph.json 노드
  2. nav_edges  — graph.json 엣지 (양방향 비디오 포함)
  3. geojson_files — 건물별 GeoJSON (outline / room / wall / collider)
  4. video_files   — 영상 파일 경로 + video_settings.json 의 yaw
"""

import json
import os
import sys
from pathlib import Path

import psycopg2

# ──────────────────────────────────────────────────────────────
# 경로 설정
# ──────────────────────────────────────────────────────────────
SKKU_NAV     = Path(__file__).resolve().parent.parent.parent   # c:\Users\admin\SKKU_NAV
FRONTEND_APP = SKKU_NAV / "SKKU_navigation_frontend" / "2.5d_indoor_navigation_frontend_v2"
GEOJSON_DIR  = FRONTEND_APP / "public" / "geojson"
VIDEOS_DIR   = FRONTEND_APP / "videos"

GRAPH_JSON          = GEOJSON_DIR / "graph.json"
BUILDINGS_JSON      = GEOJSON_DIR / "buildings.json"
VIDEO_SETTINGS_JSON = GEOJSON_DIR / "video_settings.json"

# ──────────────────────────────────────────────────────────────
# DB 연결
# ──────────────────────────────────────────────────────────────
DB_CONFIG = dict(
    host="localhost", port=5432,
    dbname="skku_nav", user="skku", password="skku1234"
)

def connect() -> psycopg2.extensions.connection:
    return psycopg2.connect(**DB_CONFIG)

# ──────────────────────────────────────────────────────────────
# 건물 탐지 (outline GeoJSON bbox 기준)
# ──────────────────────────────────────────────────────────────
def load_building_bounds(buildings: list[str]) -> dict[str, tuple]:
    """{ building_code: (min_lng, min_lat, max_lng, max_lat) }"""
    bounds = {}
    for code in buildings:
        outline = GEOJSON_DIR / code / f"{code}_outline.geojson"
        if not outline.exists():
            continue
        with open(outline, encoding="utf-8") as f:
            fc = json.load(f)
        coords: list[list[float]] = []
        def collect(arr):
            if arr and isinstance(arr[0], (int, float)):
                coords.append(arr)
            else:
                for c in arr:
                    collect(c)
        for feat in fc.get("features", []):
            geom = feat.get("geometry", {})
            if "coordinates" in geom:
                collect(geom["coordinates"])
        if coords:
            lngs = [c[0] for c in coords]
            lats = [c[1] for c in coords]
            bounds[code] = (min(lngs), min(lats), max(lngs), max(lats))
    return bounds

def detect_building(lng: float, lat: float, bounds: dict) -> str:
    for code, (min_lng, min_lat, max_lng, max_lat) in bounds.items():
        if min_lng <= lng <= max_lng and min_lat <= lat <= max_lat:
            return code
    return ""

# ──────────────────────────────────────────────────────────────
# 1. nav_nodes + nav_edges
# ──────────────────────────────────────────────────────────────
def import_graph(conn, graph: dict, bounds: dict) -> None:
    nodes = graph["nodes"]
    edges = graph["edges"]

    # ── 노드 ──────────────────────────────────────────────────
    node_meta: dict[str, dict] = {}   # id → {building, level}
    node_rows = []
    for node_id, n in nodes.items():
        lng, lat = n["coordinates"]
        level = n["level"] if isinstance(n["level"], int) else n["level"][0]
        building = detect_building(lng, lat, bounds) or "eng1"
        vertical_id = n.get("verticalId")
        node_meta[node_id] = {"building": building, "level": level}
        node_rows.append((
            node_id, building, level,
            n["type"], n.get("label", ""),
            f"SRID=4326;POINT({lng} {lat})",
            vertical_id,
        ))

    with conn.cursor() as cur:
        cur.executemany("""
            INSERT INTO nav_nodes (id, building, level, type, label, location, vertical_id)
            VALUES (%s, %s, %s, %s, %s, ST_GeomFromEWKT(%s), %s)
            ON CONFLICT (id) DO UPDATE SET
                building    = EXCLUDED.building,
                level       = EXCLUDED.level,
                type        = EXCLUDED.type,
                label       = EXCLUDED.label,
                location    = EXCLUDED.location,
                vertical_id = EXCLUDED.vertical_id
        """, node_rows)

    # ── 엣지 ──────────────────────────────────────────────────
    edge_rows = []
    for e in edges:
        from_id = e["from"]
        to_id   = e["to"]
        fm = node_meta.get(from_id, {})
        tm = node_meta.get(to_id, {})
        edge_rows.append((
            f"edge-{from_id}-{to_id}",
            from_id, to_id,
            float(e["weight"]),
            fm.get("building", ""),
            fm.get("level"),
            tm.get("level"),
            e.get("videoFwd"),
            e.get("videoFwdStart"),
            e.get("videoFwdEnd"),
            e.get("videoFwdExit"),
            e.get("videoFwdExitStart"),
            e.get("videoFwdExitEnd"),
            e.get("videoRev"),
            e.get("videoRevStart"),
            e.get("videoRevEnd"),
            e.get("videoRevExit"),
            e.get("videoRevExitStart"),
            e.get("videoRevExitEnd"),
        ))

    with conn.cursor() as cur:
        cur.executemany("""
            INSERT INTO nav_edges (
                id, from_node_id, to_node_id, weight,
                building, from_level, to_level,
                video_fwd, video_fwd_start, video_fwd_end,
                video_fwd_exit, video_fwd_exit_start, video_fwd_exit_end,
                video_rev, video_rev_start, video_rev_end,
                video_rev_exit, video_rev_exit_start, video_rev_exit_end
            ) VALUES (
                %s, %s, %s, %s,
                %s, %s, %s,
                %s, %s, %s,
                %s, %s, %s,
                %s, %s, %s,
                %s, %s, %s
            )
            ON CONFLICT (id) DO UPDATE SET
                weight               = EXCLUDED.weight,
                building             = EXCLUDED.building,
                from_level           = EXCLUDED.from_level,
                to_level             = EXCLUDED.to_level,
                video_fwd            = EXCLUDED.video_fwd,
                video_fwd_start      = EXCLUDED.video_fwd_start,
                video_fwd_end        = EXCLUDED.video_fwd_end,
                video_fwd_exit       = EXCLUDED.video_fwd_exit,
                video_fwd_exit_start = EXCLUDED.video_fwd_exit_start,
                video_fwd_exit_end   = EXCLUDED.video_fwd_exit_end,
                video_rev            = EXCLUDED.video_rev,
                video_rev_start      = EXCLUDED.video_rev_start,
                video_rev_end        = EXCLUDED.video_rev_end,
                video_rev_exit       = EXCLUDED.video_rev_exit,
                video_rev_exit_start = EXCLUDED.video_rev_exit_start,
                video_rev_exit_end   = EXCLUDED.video_rev_exit_end
        """, edge_rows)

    conn.commit()
    print(f"  nav_nodes : {len(node_rows)} 건 upsert")
    print(f"  nav_edges : {len(edge_rows)} 건 upsert")

# ──────────────────────────────────────────────────────────────
# 2. geojson_files
# ──────────────────────────────────────────────────────────────
def import_geojson(conn, buildings: list[str]) -> None:
    rows = []   # (building, level, file_type, content_str)

    for code in buildings:
        bdir = GEOJSON_DIR / code
        if not bdir.exists():
            print(f"  [WARN] {bdir} 없음 — 건너뜀")
            continue

        # manifest에서 층 목록 읽기
        mf = bdir / "manifest.json"
        levels: list[int] = []
        if mf.exists():
            with open(mf, encoding="utf-8") as f:
                levels = json.load(f).get("levels", [])

        # outline (level = NULL)
        outline = bdir / f"{code}_outline.geojson"
        if outline.exists():
            with open(outline, encoding="utf-8") as f:
                rows.append((code, None, "outline", f.read()))

        # 층별 파일
        for level in levels:
            for ftype in ("room", "wall", "collider"):
                path = bdir / f"{code}_{ftype}_L{level}.geojson"
                if path.exists():
                    with open(path, encoding="utf-8") as f:
                        rows.append((code, level, ftype, f.read()))

    with conn.cursor() as cur:
        # 멱등성: 기존 데이터 삭제 후 재삽입
        for code in buildings:
            cur.execute("DELETE FROM geojson_files WHERE building = %s", (code,))
        cur.executemany("""
            INSERT INTO geojson_files (building, level, file_type, content)
            VALUES (%s, %s, %s, %s::jsonb)
        """, rows)
        inserted = cur.rowcount

    conn.commit()
    print(f"  geojson_files : {inserted}/{len(rows)} 건 삽입")

# ──────────────────────────────────────────────────────────────
# 3. video_files
# ──────────────────────────────────────────────────────────────
def import_videos(conn, video_settings: dict) -> None:
    rows = []   # (file_name, file_path, yaw, size_bytes)

    if not VIDEOS_DIR.exists():
        print(f"  [WARN] 영상 디렉토리 없음: {VIDEOS_DIR}")
        return

    for mp4_dir in sorted(VIDEOS_DIR.iterdir()):
        if not mp4_dir.is_dir():
            continue
        for mp4_file in sorted(mp4_dir.iterdir()):
            if mp4_file.suffix.lower() != ".mp4":
                continue
            fname = mp4_file.name
            fpath = str(mp4_file.resolve())
            size  = mp4_file.stat().st_size
            yaw   = video_settings.get(fname, {}).get("yaw")   # 없으면 NULL
            rows.append((fname, fpath, yaw, size))

    with conn.cursor() as cur:
        cur.executemany("""
            INSERT INTO video_files (file_name, file_path, yaw, size_bytes)
            VALUES (%s, %s, %s, %s)
            ON CONFLICT (file_name) DO UPDATE
                SET file_path = EXCLUDED.file_path,
                    yaw       = EXCLUDED.yaw,
                    size_bytes = EXCLUDED.size_bytes
        """, rows)
        inserted = cur.rowcount

    conn.commit()
    print(f"  video_files   : {inserted}/{len(rows)} 건 upsert")

# ──────────────────────────────────────────────────────────────
# main
# ──────────────────────────────────────────────────────────────
def main() -> None:
    print("=== SKKU NAV DB Import ===\n")

    # 파일 존재 확인
    for path in (GRAPH_JSON, BUILDINGS_JSON, VIDEO_SETTINGS_JSON):
        if not path.exists():
            print(f"[ERROR] 파일 없음: {path}")
            sys.exit(1)

    with open(BUILDINGS_JSON, encoding="utf-8") as f:
        buildings: list[str] = json.load(f)
    with open(GRAPH_JSON, encoding="utf-8") as f:
        graph = json.load(f)
    with open(VIDEO_SETTINGS_JSON, encoding="utf-8") as f:
        video_settings = json.load(f)

    print(f"Buildings       : {buildings}")
    print(f"Nodes / Edges   : {len(graph['nodes'])} / {len(graph['edges'])}")
    print(f"Video settings  : {len(video_settings)} 항목")

    bounds = load_building_bounds(buildings)
    print(f"Building bounds : {list(bounds.keys())}\n")

    conn = connect()
    try:
        print("[1/3] 그래프 (nodes + edges) 적재 중...")
        import_graph(conn, graph, bounds)

        print("\n[2/3] GeoJSON 파일 적재 중...")
        import_geojson(conn, buildings)

        print("\n[3/3] 영상 파일 메타데이터 적재 중...")
        import_videos(conn, video_settings)
    finally:
        conn.close()

    print("\n=== 완료 ===")

if __name__ == "__main__":
    main()
