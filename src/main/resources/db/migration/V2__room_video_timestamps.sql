-- ──────────────────────────────────────────────────────────────
-- V2: 방 노드에 영상 클리핑 타임스탬프 추가
--
-- 복도/계단 엣지의 영상을 방 노드의 타임스탬프 기준으로 잘라서 전송한다.
--
-- 순방향(fwd): from→to 방향의 복도 영상 (예: cw 방향)
-- 역방향(rev): to→from 방향의 복도 영상 (예: ccw 방향)
--
-- clip_start: 방 문이 영상에 처음 나타나는 시점 (밀리초)
-- clip_end  : 방 문 통과가 완료되는 시점 (밀리초)
--
-- 예시)
--   복도 영상 전체: 0ms ~ 60000ms
--   Room A가 영상 5000ms에 등장 ~ 7500ms에 통과 완료
--     → clip_fwd_start = 5000, clip_fwd_end = 7500
--   역방향 영상에서는 52500ms~55000ms 구간
--     → clip_rev_start = 52500, clip_rev_end = 55000
-- ──────────────────────────────────────────────────────────────

ALTER TABLE nav_nodes
    ADD COLUMN clip_fwd_start BIGINT,   -- 순방향 영상: 방 등장 시작 (밀리초)
    ADD COLUMN clip_fwd_end   BIGINT,   -- 순방향 영상: 방 통과 완료 (밀리초)
    ADD COLUMN clip_rev_start BIGINT,   -- 역방향 영상: 방 등장 시작 (밀리초)
    ADD COLUMN clip_rev_end   BIGINT;   -- 역방향 영상: 방 통과 완료 (밀리초)

COMMENT ON COLUMN nav_nodes.clip_fwd_start IS '순방향 복도 영상에서 이 방 문이 등장하는 시작 타임스탬프 (밀리초)';
COMMENT ON COLUMN nav_nodes.clip_fwd_end   IS '순방향 복도 영상에서 이 방 문 통과가 완료되는 타임스탬프 (밀리초)';
COMMENT ON COLUMN nav_nodes.clip_rev_start IS '역방향 복도 영상에서 이 방 문이 등장하는 시작 타임스탬프 (밀리초)';
COMMENT ON COLUMN nav_nodes.clip_rev_end   IS '역방향 복도 영상에서 이 방 문 통과가 완료되는 타임스탬프 (밀리초)';
