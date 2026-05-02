-- V5: nav_nodes 에 room 전용 컬럼 추가
--   name      — 방 표시 이름 (예: "소프트웨어학과 실험실")
--   room_type — 방 세부 유형 (예: lecture / lab / office / restroom)

ALTER TABLE nav_nodes
    ADD COLUMN IF NOT EXISTS name      VARCHAR(200) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS room_type VARCHAR(30)  NOT NULL DEFAULT '';

-- 방 이름/유형 검색 성능을 위한 인덱스
CREATE INDEX IF NOT EXISTS idx_nav_nodes_name      ON nav_nodes (name);
CREATE INDEX IF NOT EXISTS idx_nav_nodes_room_type ON nav_nodes (room_type);
