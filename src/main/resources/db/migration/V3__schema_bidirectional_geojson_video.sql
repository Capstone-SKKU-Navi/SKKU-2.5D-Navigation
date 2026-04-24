-- ══════════════════════════════════════════════════════════════
-- V3: 전면 스키마 개편
--   1. nav_nodes    — vertical_id 추가, stale V2 컬럼 제거
--   2. nav_edges    — 양방향 비디오 컬럼 / 초(s) 단위 / building + level 추가
--   3. geojson_files — 건물 GeoJSON JSONB 저장
--   4. video_files  — 영상 메타데이터 + 서버 경로 + yaw
-- ══════════════════════════════════════════════════════════════

-- ──────────────────────────────────────────────────────────────
-- 1. nav_nodes
-- ──────────────────────────────────────────────────────────────

-- stairs / elevator 물리적 유닛 식별자 (stairs: 1~4, elevator: 1~2)
ALTER TABLE nav_nodes ADD COLUMN IF NOT EXISTS vertical_id INTEGER;

-- V2(room_video_timestamps)가 적용된 경우 제거
ALTER TABLE nav_nodes DROP COLUMN IF EXISTS clip_fwd_start;
ALTER TABLE nav_nodes DROP COLUMN IF EXISTS clip_fwd_end;
ALTER TABLE nav_nodes DROP COLUMN IF EXISTS clip_rev_start;
ALTER TABLE nav_nodes DROP COLUMN IF EXISTS clip_rev_end;

-- ──────────────────────────────────────────────────────────────
-- 2. nav_edges — 컬럼 이름 변경 (old → new, 방어적 처리)
-- ──────────────────────────────────────────────────────────────

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'nav_edges' AND column_name = 'video_name'
    ) THEN
        ALTER TABLE nav_edges RENAME COLUMN video_name TO video_fwd;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'nav_edges' AND column_name = 'video_start'
    ) THEN
        ALTER TABLE nav_edges RENAME COLUMN video_start TO video_fwd_start;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'nav_edges' AND column_name = 'video_end'
    ) THEN
        ALTER TABLE nav_edges RENAME COLUMN video_end TO video_fwd_end;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'nav_edges' AND column_name = 'video_exit'
    ) THEN
        ALTER TABLE nav_edges RENAME COLUMN video_exit TO video_fwd_exit;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'nav_edges' AND column_name = 'video_exit_start'
    ) THEN
        ALTER TABLE nav_edges RENAME COLUMN video_exit_start TO video_fwd_exit_start;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'nav_edges' AND column_name = 'video_exit_end'
    ) THEN
        ALTER TABLE nav_edges RENAME COLUMN video_exit_end TO video_fwd_exit_end;
    END IF;
END $$;

-- BIGINT(ms) → DOUBLE PRECISION(초) 타입 변환
ALTER TABLE nav_edges
    ALTER COLUMN video_fwd_start      TYPE DOUBLE PRECISION
        USING video_fwd_start::DOUBLE PRECISION,
    ALTER COLUMN video_fwd_end        TYPE DOUBLE PRECISION
        USING video_fwd_end::DOUBLE PRECISION,
    ALTER COLUMN video_fwd_exit_start TYPE DOUBLE PRECISION
        USING video_fwd_exit_start::DOUBLE PRECISION,
    ALTER COLUMN video_fwd_exit_end   TYPE DOUBLE PRECISION
        USING video_fwd_exit_end::DOUBLE PRECISION;

-- 역방향 비디오 컬럼 추가
ALTER TABLE nav_edges
    ADD COLUMN IF NOT EXISTS video_rev              VARCHAR(200),
    ADD COLUMN IF NOT EXISTS video_rev_start        DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS video_rev_end          DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS video_rev_exit         VARCHAR(200),
    ADD COLUMN IF NOT EXISTS video_rev_exit_start   DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS video_rev_exit_end     DOUBLE PRECISION;

-- 건물 코드 + 층 수 (edge에도 저장)
ALTER TABLE nav_edges
    ADD COLUMN IF NOT EXISTS building   VARCHAR(20) DEFAULT '',
    ADD COLUMN IF NOT EXISTS from_level INTEGER,
    ADD COLUMN IF NOT EXISTS to_level   INTEGER;

-- 런타임 계산으로 대체된 컬럼 제거
ALTER TABLE nav_edges DROP COLUMN IF EXISTS clip_start;
ALTER TABLE nav_edges DROP COLUMN IF EXISTS clip_end;

-- ──────────────────────────────────────────────────────────────
-- 3. geojson_files — 건물 GeoJSON JSONB 저장
--    프론트엔드에 /api/geojson/all 으로 단일 FeatureCollection 반환
-- ──────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS geojson_files (
    id          BIGSERIAL    PRIMARY KEY,
    building    VARCHAR(20)  NOT NULL,
    level       INTEGER,                   -- NULL = 건물 전체(outline)
    file_type   VARCHAR(30)  NOT NULL,     -- 'outline' | 'room' | 'wall' | 'collider'
    content     JSONB        NOT NULL,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_geojson_building_level ON geojson_files (building, level);
CREATE INDEX IF NOT EXISTS idx_geojson_file_type      ON geojson_files (file_type);

-- ──────────────────────────────────────────────────────────────
-- 4. video_files — 영상 메타데이터
--    Spring ResourceRegion range request 시 file_path로 FileSystemResource 생성
-- ──────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS video_files (
    id          BIGSERIAL     PRIMARY KEY,
    file_name   VARCHAR(200)  NOT NULL UNIQUE,  -- 'eng1_c_F1_1_cw.mp4'
    file_path   VARCHAR(500)  NOT NULL,          -- 서버 절대경로
    yaw         DOUBLE PRECISION,                -- 카메라 방위각 (도, video_settings.json)
    size_bytes  BIGINT
);

CREATE INDEX IF NOT EXISTS idx_video_file_name ON video_files (file_name);
