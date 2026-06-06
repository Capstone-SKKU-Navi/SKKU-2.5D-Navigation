package com.skku.nav.dto;

/**
 * POST /api/route 요청의 출발/도착 좌표.
 *
 * isRoom — 해당 좌표가 방(실내 공간)을 가리키는지 여부. true면 수선의 발 투영 시
 *          실외(outside) edge 보다 실내 edge 를 우선 선택한다. JSON 에 필드가
 *          없으면 false (기존 클라이언트 호환 → 종전 동작 유지).
 */
public record RouteCoord(double lng, double lat, int level, boolean isRoom) {}
