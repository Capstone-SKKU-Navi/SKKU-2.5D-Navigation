# SKKU Navigation Backend

성균관대학교 자연과학캠퍼스 실내 내비게이션 시스템의 REST API 백엔드.

## 기술 스택

| 역할 | 기술 |
|------|------|
| 프레임워크 | Spring Boot 3.5.14 |
| 언어 | Java 21 |
| 빌드 도구 | Gradle (Wrapper) |
| DB | PostgreSQL 16 + PostGIS 3.4 |
| ORM | Spring Data JPA + Hibernate 6.6 Spatial |
| DB 마이그레이션 | Flyway (V1 / V3 / V4) |
| 컨테이너 | Docker |

---

## 아키텍처

```
Frontend (TypeScript)
  POST /api/route          → RouteBuilderService  (수선의 발 + Dijkstra + 클립 조립)
  GET  /api/geojson/all    → GeojsonService       (GeoJSON 병합 + 속성 주입)
  GET  /api/videos/{f}     → VideoController      (HTTP Range 스트리밍)
  GET  /api/nodes|graph    → NodeController       (그래프 데이터 조회)
                                    │
                          PostgreSQL 16 + PostGIS
                          (nav_nodes, nav_edges, geojson_files, video_files)
```

---

## 프로젝트 구조

```
src/main/java/com/skku/nav/
├── controller/
│   ├── RouteController.java       POST /api/route, GET /api/route (레거시)
│   ├── GeojsonController.java     GET  /api/geojson/all
│   ├── VideoController.java       GET  /api/videos/{filename}
│   └── NodeController.java        GET  /api/nodes, /api/graph
├── service/
│   ├── RouteBuilderService.java   수선의 발 + 4조합 Dijkstra + 클립 조립 (핵심)
│   ├── GraphService.java          그래프 인메모리 캐시 + 층별 엣지 그룹핑
│   ├── GeojsonService.java        GeoJSON 병합 + _building/_level/_featureType 주입
│   ├── VideoStreamCache.java      영상 경로/yaw 인메모리 캐시 (fileName → path/yaw)
│   └── RouteService.java          레거시 label 기반 경로 탐색
├── entity/
│   ├── NavNode.java, NavEdge.java
│   ├── GeojsonFile.java, VideoFile.java
├── dto/
│   ├── RouteCoord.java            {lng, lat, level}
│   ├── ApiRouteRequestDto.java    POST /api/route 요청
│   ├── ApiRouteResponseDto.java   POST /api/route 응답
│   ├── ApiRouteClipDto.java       영상 클립 1개
│   └── ...
└── repository/

src/main/resources/
├── application.yml
└── db/migration/
    ├── V1__init_schema.sql                          기본 스키마 (nav_nodes, nav_edges)
    ├── V3__schema_bidirectional_geojson_video.sql   양방향 영상 + geojson_files + video_files
    └── V4__drop_unused_schema.sql                   미사용 search_history / nav_edges_pgr 정리
```

---

## 사전 요구사항

- **Java 21** (JDK), `JAVA_HOME` 환경변수 설정
- **Docker Desktop** (실행 중 상태)
- **Python 3.9+** (데이터 임포트용)

---

## 실행 방법

### 1. PostgreSQL 기동

```powershell
docker run --name skku_nav_db `
  -e POSTGRES_DB=skku_nav -e POSTGRES_USER=skku -e POSTGRES_PASSWORD=skku1234 `
  -p 5432:5432 -d postgis/postgis:16-3.4
```

`docker ps` 에서 `(healthy)` 확인 후 다음 단계 진행.

### 2. 데이터 임포트

```powershell
cd SKKU_navigation_backend
pip install psycopg2-binary
python scripts/import_to_db.py
```

임포트 내용:
- `geojson/graph.json` → `nav_nodes` 27건, `nav_edges` 20건
- `geojson/**/*.geojson` → `geojson_files` 16건
- 영상 디렉터리 스캔 → `video_files` 655건

### 3. 백엔드 기동

```powershell
.\gradlew.bat bootRun
```

기동 시 Flyway가 V1→V3→V4 마이그레이션을 자동 실행하고, `GraphService` · `VideoStreamCache` 가 DB 데이터를 인메모리에 로드합니다.

서버: `http://localhost:8080`

---

## REST API

### `POST /api/route` — 좌표 기반 경로 탐색 (주 API)

```json
// 요청
{ "from": {"lng": 126.9770, "lat": 37.2942, "level": 1},
  "to":   {"lng": 126.9766, "lat": 37.2943, "level": 4} }

// 응답 (found=true)
{
  "found": true,
  "route": {
    "coordinates": [[126.9770, 37.2942], ...],
    "levels": [1, 1, 2, 4, 4],
    "totalDistance": 56.7,
    "estimatedTime": "약 1분",
    "startLevel": 1, "endLevel": 4
  },
  "walkthrough": {
    "clips": [
      { "index": 0, "videoFile": "eng1_c_F1_3_cw.mp4",
        "videoStart": 10.4, "videoEnd": 38.1, "duration": 27.7,
        "yaw": 193.2, "level": 1, "isExitClip": false,
        "coordStartIdx": 1, "coordEndIdx": 2,
        "routeDistStart": 0.0, "routeDistEnd": 18.4 },
      ...
    ],
    "videoStartCoordIdx": 1, "videoEndCoordIdx": 7
  }
}

// 응답 (found=false)
{ "found": false, "error": "경로를 찾을 수 없습니다" }
```

**알고리즘:**
1. 입력 좌표 → 동일 층 복도 엣지에 수선의 발 투영 (층별 엣지 그룹핑으로 탐색 범위 축소)
2. 출발/도착 엣지 endpoint 4조합 Dijkstra → 최단 경로 선택
3. 백트래킹 제거 → 좌표/층 배열 조립
4. 복도 클립 (부분 타임스탬프) / 계단·엘리베이터 클립 (진입+진출 쌍) 조립
5. 소요 시간 = `ceil(totalDistance / 72 m/min)` 분

---

### `GET /api/geojson/all`

모든 건물 GeoJSON을 단일 FeatureCollection으로 병합. 각 Feature에 `_building`, `_level`, `_featureType` 속성 주입.

현재 437개 Feature (eng1: outline 1 + 방 431 + collider 5).

---

### `GET /api/videos/{filename}`

HTTP Range request 기반 영상 스트리밍 (`206 Partial Content`). 기동 시 655건 메타데이터를 `ConcurrentHashMap`에 캐시하여 DB 부하 없음.

캐시 수동 갱신: `POST /api/videos/reload`

---

### `GET /api/nodes`

쿼리 파라미터: `?building=eng1`, `?level=1`, `?building=eng1&level=1`

### `GET /api/graph`

전체 그래프 JSON (노드 + 엣지). 프론트엔드 로컬 모드에서 사용.

### `GET /api/route?from={label}&to={label}` (레거시)

label 기반 경로 탐색. nav_edges는 복도/계단/엘리베이터 노드만 연결하므로 room 노드 간 경로는 반환되지 않음. 좌표 기반 `POST /api/route` 사용 권장.

---

## DB 스키마

| 테이블 | 주요 컬럼 |
|--------|-----------|
| `nav_nodes` | id, building, level, type, label, location (PostGIS POINT) |
| `nav_edges` | id, from/to_node_id, weight, video_fwd/rev, video_fwd/rev_start/end (초, DOUBLE) |
| `geojson_files` | building, level, file_type, content (JSONB) |
| `video_files` | file_name (UNIQUE), file_path, yaw |

---

## DB 접속 정보

| 항목 | 값 |
|------|----|
| Host | `localhost:5432` |
| Database | `skku_nav` |
| User | `skku` |
| Password | `skku1234` |

---

## API 동작 확인 결과 (Spring Boot 3.5.14)

| 엔드포인트 | 결과 |
|---|---|
| `GET /api/nodes` | ✅ 27건, 필터 정상 |
| `GET /api/graph` | ✅ 노드 27, 엣지 20 |
| `GET /api/geojson/all` | ✅ 437 features |
| `POST /api/route` (동층) | ✅ found=true, 좌표 4개, 클립 1개 |
| `POST /api/route` (다층 L1→L4) | ✅ found=true, 좌표 8개, 클립 4개 |
| `POST /api/route` (범위 외) | ✅ found=false 정상 반환 |
| `GET /api/videos/{filename}` | ✅ 206 Partial Content |
| `GET /api/route` (레거시) | ⚠️ found=false (room 노드 미연결 — 설계상) |
