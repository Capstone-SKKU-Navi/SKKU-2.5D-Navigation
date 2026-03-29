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
    │   │   ├── NavNode.java                   # 노드 엔티티 (PostGIS Point)
    │   │   └── NavEdge.java                   # 엣지 엔티티 (360° 비디오 메타데이터)
    │   ├── dto/
    │   │   ├── NodeDto.java
    │   │   ├── EdgeDto.java
    │   │   ├── GraphDto.java
    │   │   ├── RouteRequestDto.java
    │   │   └── RouteResponseDto.java
    │   ├── repository/
    │   │   ├── NavNodeRepository.java         # ST_DWithin 근접 쿼리 포함
    │   │   └── NavEdgeRepository.java
    │   ├── service/
    │   │   ├── GraphService.java              # 인메모리 그래프 캐시 + 인접 리스트
    │   │   └── RouteService.java              # Dijkstra 경로 탐색
    │   └── controller/
    │       ├── NodeController.java
    │       └── RouteController.java
    └── resources/
        ├── application.yml                    # DB 연결 설정
        └── db/migration/
            └── V1__init_schema.sql            # PostGIS + pgRouting 스키마
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

> Spring Boot가 시작될 때 Flyway가 자동으로 `V1__init_schema.sql`을 실행하여
> PostGIS, pgRouting 확장 및 테이블을 생성합니다.

### 3. 동작 확인

```powershell
curl http://localhost:8080/api/graph
# 응답: {"nodes":[],"edges":[]}
```

---

## REST API

기본 URL: `http://localhost:8080`

### 그래프

| Method | URL | 설명 |
|--------|-----|------|
| `GET` | `/api/graph` | 전체 노드 + 엣지 반환 (프론트엔드 초기 로드용) |
| `POST` | `/api/graph/reload` | 인메모리 그래프 캐시 수동 갱신 |

### 노드

| Method | URL | 설명 |
|--------|-----|------|
| `GET` | `/api/nodes` | 전체 노드 목록 |
| `GET` | `/api/nodes?building=ENG1&level=2` | building, level 필터 |
| `GET` | `/api/nodes/{id}` | 단일 노드 조회 |
| `GET` | `/api/nodes/search?q=21223` | 방 번호/이름 검색 |
| `GET` | `/api/nodes/nearby?lng=126.97&lat=37.29&radius=100&limit=5` | 반경 내 근접 노드 (PostGIS) |

### 경로 탐색

| Method | URL | 설명 |
|--------|-----|------|
| `POST` | `/api/route` | Dijkstra 경로 탐색 |
| `GET` | `/api/route?from={nodeId}&to={nodeId}` | 쿼리 파라미터 방식 |

#### POST /api/route 요청 예시

```json
{
  "fromNodeId": "node-mn8ztuvt-pb81",
  "toNodeId": "node-mn91s0re-dmpv"
}
```

#### 응답 예시

```json
{
  "found": true,
  "totalDistance": 45.2,
  "path": [
    { "id": "node-mn8ztuvt-pb81", "level": 1, "type": "corridor", ... },
    { "id": "node-mn91s0re-dmpv", "level": 1, "type": "room", "label": "21223", ... }
  ],
  "edges": [
    { "id": "...", "from": "...", "to": "...", "weight": 45.2, "videoFwd": "eng1_corridor_23_1F_cw.mp4", ... }
  ]
}
```

---

## DB 스키마

```
nav_nodes
├── id          VARCHAR(50)   PK
├── building    VARCHAR(20)
├── level       INTEGER
├── type        VARCHAR(20)   -- corridor | room | stairs | elevator | entrance
├── label       VARCHAR(100)  -- 방 번호 (예: 21223)
└── location    GEOMETRY(POINT, 4326)  -- WGS84 경위도 (PostGIS)

nav_edges
├── id                   VARCHAR(120)  PK
├── from_node_id         VARCHAR(50)   FK → nav_nodes
├── to_node_id           VARCHAR(50)   FK → nav_nodes
├── weight               DOUBLE        -- 거리(미터)
├── video_fwd / video_rev              -- 순방향/역방향 360° 영상 파일명
├── video_fwd_start/end                -- 영상 타임스탬프 (초)
└── video_fwd_exit / video_rev_exit    -- 계단·엘리베이터 진출 클립
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
- [ ] 방 GeoJSON 폴리곤 저장 및 `ST_Contains` 기반 위치→방 매핑 API
- [ ] pgRouting `pgr_dijkstra()` 서버사이드 경로탐색 적용
- [ ] JWT 기반 인증 (관리자용 그래프 편집 API)
- [ ] 건물 다층(3~5층) 데이터 추가 대응
