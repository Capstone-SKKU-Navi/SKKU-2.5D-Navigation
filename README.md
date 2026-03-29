# SKKU Navigation Backend

성균관대학교 자연과학캠퍼스 실내 내비게이션 시스템의 REST API 백엔드.

## 기술 스택

| 역할 | 기술 |
|------|------|
| 프레임워크 | Spring Boot 3.3.4 |
| 언어 | Java 21 |
| 빌드 도구 | Gradle 8.10.2 (Wrapper) |
| DB | PostgreSQL 16 + PostGIS 3.4 + pgRouting 3.6 |
| ORM | Spring Data JPA + Hibernate Spatial |
| DB 마이그레이션 | Flyway |
| 컨테이너 | Docker Compose |

---

## 역할 및 구조

프론트엔드는 GeoJSON 폴리곤(방·벽·충돌체)과 `graph.json`을 **로컬 정적 파일**에서 직접 로드하며, 경로 탐색도 자체 Dijkstra로 수행한다. 백엔드의 역할은 **방 번호를 입력받아 최단 경로를 계산하고 JSON으로 반환**하는 것 하나다.

```
프론트엔드 → GET /api/route?from=21223&to=21517 → 백엔드
                                                      ↓
                                          DB에서 노드/엣지 로드
                                                      ↓
                                          Dijkstra 최단경로 탐색
                                                      ↓
                                    { path, edges, totalDistance, estimatedTime }
```

---

## 프로젝트 구조

```
SKKU_navigation_backend/
├── docker-compose.yaml                        # DB 컨테이너 정의
├── build.gradle                               # 의존성 관리
├── gradlew.bat                                # Windows Gradle wrapper
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
└── src/main/
    ├── java/com/skku/nav/
    │   ├── SkkuNavApplication.java            # 진입점
    │   ├── config/
    │   │   └── WebConfig.java                 # CORS 설정 (localhost:8082 허용)
    │   ├── entity/
    │   │   ├── NavNode.java                   # 노드 엔티티 (PostGIS Point + clip 타임스탬프)
    │   │   └── NavEdge.java                   # 엣지 엔티티 (360° 영상 메타데이터, ms 단위)
    │   ├── dto/
    │   │   ├── NodeDto.java
    │   │   ├── EdgeDto.java
    │   │   ├── GraphDto.java
    │   │   ├── RouteEdgeDto.java              # 프론트엔드 RouteEdge 형식
    │   │   └── RouteResponseDto.java          # 프론트엔드 RouteResponse 형식
    │   ├── repository/
    │   │   ├── NavNodeRepository.java         # label 검색, ST_DWithin 근접 쿼리
    │   │   └── NavEdgeRepository.java
    │   ├── service/
    │   │   ├── GraphService.java              # 인메모리 그래프 캐시 + 인접 리스트
    │   │   └── RouteService.java              # Dijkstra 경로 탐색 + 방 clip 적용
    │   └── controller/
    │       ├── NodeController.java            # 관리용 노드 조회 API
    │       └── RouteController.java           # 핵심 경로 탐색 API
    └── resources/
        ├── application.yml                    # DB 연결 설정
        └── db/migration/
            ├── V1__init_schema.sql            # PostGIS + pgRouting 스키마
            ├── V2__room_video_timestamps.sql  # 방 노드 clip 타임스탬프 (BIGINT, ms)
            └── V3__edge_timestamps_to_ms.sql  # 엣지 타임스탬프 DOUBLE → BIGINT(ms) 통일
```

---

## 사전 요구사항

- **Java 21** (JDK)
- **Docker Desktop** (실행 중 상태여야 함)
- `JAVA_HOME` 환경변수가 Java 21을 가리킬 것

### JAVA_HOME 설정 (Windows)

```powershell
# 현재 세션 적용
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"

# 영구 적용 (관리자 PowerShell)
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Java\jdk-21", "Machine")
```

---

## 실행 방법

### 1. DB 컨테이너 시작

```powershell
cd SKKU_navigation_backend
docker compose up -d
```

컨테이너가 정상적으로 올라왔는지 확인:

```powershell
docker compose ps
```

`skku_nav_db` 상태가 `healthy`이면 준비 완료.

### 2. Spring Boot 실행

```powershell
.\gradlew.bat bootRun
```

처음 실행 시 Gradle이 의존성을 다운로드하므로 **5~10분** 소요될 수 있습니다.

다음 메시지가 나오면 정상 실행:

```
Started SkkuNavApplication in X.XXX seconds (process running for X.XXX)
```

> Spring Boot가 시작될 때 Flyway가 V1~V3 마이그레이션을 순서대로 자동 실행합니다.

### 3. 동작 확인

```powershell
curl "http://localhost:8080/api/route?from=21223&to=21517"
```

---

## REST API

기본 URL: `http://localhost:8080`

### 핵심 API — 경로 탐색

| Method | URL | 설명 |
|--------|-----|------|
| `GET` | `/api/route?from={방번호}&to={방번호}` | 방 번호 기반 최단경로 탐색 |

#### 요청 예시

```
GET /api/route?from=21223&to=21517
```

#### 응답 형식

```json
{
  "found": true,
  "path": ["node-abc", "node-def", "node-ghi"],
  "edges": [
    {
      "from": "node-abc",
      "to": "node-def",
      "video": "eng1_corridor_23_1F_cw.mp4",
      "videoStart": 5000,
      "videoEnd": 27000,
      "duration": 22.0
    }
  ],
  "totalDistance": 45.2,
  "estimatedTime": "약 1분"
}
```

- `videoStart` / `videoEnd`: 영상 클리핑 구간 **(밀리초)**
- `duration`: 클리핑 구간 길이 (초)
- 경로를 찾지 못한 경우: `{ "found": false, ... }`

#### 프론트엔드 연동

`apiClient.ts`에서 `useMock = false`로 변경하면 바로 연동됩니다.

---

### 관리용 API

| Method | URL | 설명 |
|--------|-----|------|
| `GET` | `/api/graph` | 전체 노드 + 엣지 반환 |
| `POST` | `/api/graph/reload` | 인메모리 그래프 캐시 수동 갱신 |
| `GET` | `/api/nodes` | 전체 노드 목록 (`?building=&level=` 필터) |
| `GET` | `/api/nodes/{id}` | 단일 노드 조회 |
| `GET` | `/api/nodes/search?q=21223` | 방 번호 검색 |
| `GET` | `/api/nodes/nearby?lng=&lat=&radius=&limit=` | 반경 내 근접 노드 (PostGIS) |

---

## DB 스키마

```
nav_nodes
├── id               VARCHAR(50)           PK
├── building         VARCHAR(20)
├── level            INTEGER
├── type             VARCHAR(20)           corridor | room | stairs | elevator | entrance
├── label            VARCHAR(100)          방 번호 (경로 탐색 입력값, 예: "21223")
├── location         GEOMETRY(POINT,4326)  WGS84 경위도 (PostGIS)
├── clip_fwd_start   BIGINT                순방향 복도 영상에서 방 문 등장 시작 (밀리초)
├── clip_fwd_end     BIGINT                순방향 복도 영상에서 방 문 통과 완료 (밀리초)
├── clip_rev_start   BIGINT                역방향 복도 영상에서 방 문 등장 시작 (밀리초)
└── clip_rev_end     BIGINT                역방향 복도 영상에서 방 문 통과 완료 (밀리초)

    * clip_* 컬럼은 room 타입 노드에만 사용. 나머지는 NULL.

nav_edges
├── id                    VARCHAR(120)  PK
├── from_node_id          VARCHAR(50)   FK → nav_nodes
├── to_node_id            VARCHAR(50)   FK → nav_nodes
├── weight                DOUBLE        거리 (미터)
├── video_fwd             VARCHAR(200)  순방향 360° 영상 파일명
├── video_fwd_start       BIGINT        순방향 영상 시작 (밀리초)
├── video_fwd_end         BIGINT        순방향 영상 종료 (밀리초)
├── video_fwd_exit        VARCHAR(200)  순방향 진출 클립 — 계단·엘리베이터 전용
├── video_fwd_exit_start  BIGINT        순방향 진출 클립 시작 (밀리초)
├── video_fwd_exit_end    BIGINT        순방향 진출 클립 종료 (밀리초)
├── video_rev             VARCHAR(200)  역방향 360° 영상 파일명
├── video_rev_start       BIGINT        역방향 영상 시작 (밀리초)
├── video_rev_end         BIGINT        역방향 영상 종료 (밀리초)
├── video_rev_exit        VARCHAR(200)  역방향 진출 클립 — 계단·엘리베이터 전용
├── video_rev_exit_start  BIGINT        역방향 진출 클립 시작 (밀리초)
└── video_rev_exit_end    BIGINT        역방향 진출 클립 종료 (밀리초)

    * 모든 타임스탬프는 밀리초(ms) 단위로 통일.
```

`nav_edges_pgr` 뷰를 통해 `pgr_dijkstra()` 등 pgRouting 함수를 직접 사용할 수 있습니다.

---

## DB 접속 정보

| 항목 | 값 |
|------|----|
| Host | `localhost:5432` |
| Database | `skku_nav` |
| User | `skku` |
| Password | `skku1234` |

---

## CORS

프론트엔드 개발 서버(`localhost:8082`)의 요청을 허용하도록 설정되어 있습니다.
추가 Origin이 필요하면 `WebConfig.java`에서 수정하세요.

---

## 향후 작업

- [ ] 프론트엔드 `graph.json` 데이터를 DB로 임포트하는 스크립트 작성
- [ ] pgRouting `pgr_dijkstra()` 서버사이드 경로탐색으로 전환
- [ ] 건물 다층(3~5층) 데이터 추가 대응
- [ ] JWT 기반 인증 (관리자용 그래프 편집 API)
