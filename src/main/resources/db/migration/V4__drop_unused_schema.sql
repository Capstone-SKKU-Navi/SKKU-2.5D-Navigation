-- V1에서 생성됐으나 애플리케이션에서 사용되지 않는 스키마 정리

-- pgRouting 호환 뷰 및 시퀀스 (클라이언트 사이드 Dijkstra로 대체됨)
DROP VIEW     IF EXISTS nav_edges_pgr;
DROP SEQUENCE IF EXISTS nav_edges_seq;

-- 검색 히스토리 테이블 (미구현 기능)
DROP TABLE IF EXISTS search_history;
