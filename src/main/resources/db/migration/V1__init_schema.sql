-- PostGIS & pgRouting 확장 활성화
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgrouting;

-- ──────────────────────────────────────────────────────────────
-- 노드 테이블
-- ──────────────────────────────────────────────────────────────
CREATE TABLE nav_nodes (
    id          VARCHAR(50)  PRIMARY KEY,
    building    VARCHAR(20)  NOT NULL DEFAULT '',
    level       INTEGER      NOT NULL,
    type        VARCHAR(20)  NOT NULL
                    CHECK (type IN ('corridor','room','stairs','elevator','entrance')),
    label       VARCHAR(100) NOT NULL DEFAULT '',
    location    GEOMETRY(POINT, 4326)          -- WGS84 경위도
);

CREATE INDEX idx_nav_nodes_level    ON nav_nodes (level);
CREATE INDEX idx_nav_nodes_type     ON nav_nodes (type);
CREATE INDEX idx_nav_nodes_building ON nav_nodes (building);
CREATE INDEX idx_nav_nodes_location ON nav_nodes USING GIST (location);  -- 공간 인덱스

-- ──────────────────────────────────────────────────────────────
-- 엣지 테이블
-- ──────────────────────────────────────────────────────────────
CREATE TABLE nav_edges (
    id                   VARCHAR(120) PRIMARY KEY,
    from_node_id         VARCHAR(50)  NOT NULL REFERENCES nav_nodes(id) ON DELETE CASCADE,
    to_node_id           VARCHAR(50)  NOT NULL REFERENCES nav_nodes(id) ON DELETE CASCADE,
    weight               DOUBLE PRECISION NOT NULL,   -- 거리(미터)

    -- 순방향 (from → to) 360° 비디오
    video_fwd            VARCHAR(200),
    video_fwd_start      DOUBLE PRECISION,
    video_fwd_end        DOUBLE PRECISION,
    video_fwd_exit       VARCHAR(200),               -- 계단/엘리베이터 진출 클립
    video_fwd_exit_start DOUBLE PRECISION,
    video_fwd_exit_end   DOUBLE PRECISION,

    -- 역방향 (to → from) 360° 비디오
    video_rev            VARCHAR(200),
    video_rev_start      DOUBLE PRECISION,
    video_rev_end        DOUBLE PRECISION,
    video_rev_exit       VARCHAR(200),
    video_rev_exit_start DOUBLE PRECISION,
    video_rev_exit_end   DOUBLE PRECISION
);

CREATE INDEX idx_nav_edges_from ON nav_edges (from_node_id);
CREATE INDEX idx_nav_edges_to   ON nav_edges (to_node_id);

-- ──────────────────────────────────────────────────────────────
-- pgRouting 호환 뷰 (향후 서버사이드 Dijkstra/A* 확장용)
-- pgr_dijkstra() 등의 함수가 이 뷰를 사용할 수 있다
-- ──────────────────────────────────────────────────────────────
CREATE SEQUENCE IF NOT EXISTS nav_edges_seq;

CREATE VIEW nav_edges_pgr AS
SELECT
    nextval('nav_edges_seq')::BIGINT AS id,
    from_node_id                      AS source,
    to_node_id                        AS target,
    weight                            AS cost,
    weight                            AS reverse_cost   -- 양방향 동일 비용
FROM nav_edges;
