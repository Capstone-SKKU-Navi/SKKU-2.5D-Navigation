-- ──────────────────────────────────────────────────────────────
-- V3: nav_edges 의 영상 타임스탬프를 초(DOUBLE) → 밀리초(BIGINT) 로 통일
--     nav_nodes.clip_* 컬럼(V2)이 이미 BIGINT(ms)이므로 단위 일치
-- ──────────────────────────────────────────────────────────────

ALTER TABLE nav_edges
    ALTER COLUMN video_fwd_start      TYPE BIGINT USING ROUND(video_fwd_start      * 1000)::BIGINT,
    ALTER COLUMN video_fwd_end        TYPE BIGINT USING ROUND(video_fwd_end        * 1000)::BIGINT,
    ALTER COLUMN video_fwd_exit_start TYPE BIGINT USING ROUND(video_fwd_exit_start * 1000)::BIGINT,
    ALTER COLUMN video_fwd_exit_end   TYPE BIGINT USING ROUND(video_fwd_exit_end   * 1000)::BIGINT,
    ALTER COLUMN video_rev_start      TYPE BIGINT USING ROUND(video_rev_start      * 1000)::BIGINT,
    ALTER COLUMN video_rev_end        TYPE BIGINT USING ROUND(video_rev_end        * 1000)::BIGINT,
    ALTER COLUMN video_rev_exit_start TYPE BIGINT USING ROUND(video_rev_exit_start * 1000)::BIGINT,
    ALTER COLUMN video_rev_exit_end   TYPE BIGINT USING ROUND(video_rev_exit_end   * 1000)::BIGINT;

COMMENT ON COLUMN nav_edges.video_fwd_start      IS '순방향 영상 시작 타임스탬프 (밀리초)';
COMMENT ON COLUMN nav_edges.video_fwd_end        IS '순방향 영상 종료 타임스탬프 (밀리초)';
COMMENT ON COLUMN nav_edges.video_fwd_exit_start IS '순방향 진출 클립 시작 (밀리초) — 계단/엘리베이터 전용';
COMMENT ON COLUMN nav_edges.video_fwd_exit_end   IS '순방향 진출 클립 종료 (밀리초) — 계단/엘리베이터 전용';
COMMENT ON COLUMN nav_edges.video_rev_start      IS '역방향 영상 시작 타임스탬프 (밀리초)';
COMMENT ON COLUMN nav_edges.video_rev_end        IS '역방향 영상 종료 타임스탬프 (밀리초)';
COMMENT ON COLUMN nav_edges.video_rev_exit_start IS '역방향 진출 클립 시작 (밀리초) — 계단/엘리베이터 전용';
COMMENT ON COLUMN nav_edges.video_rev_exit_end   IS '역방향 진출 클립 종료 (밀리초) — 계단/엘리베이터 전용';
