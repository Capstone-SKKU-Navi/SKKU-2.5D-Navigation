package com.skku.nav.dto;

/**
 * POST /api/route 요청의 출발/도착 좌표.
 *
 * preferIndoor — 해당 좌표를 실내 공간으로 보고 수선의 발 투영 시 실외(outside)
 *                edge 보다 실내 edge 를 우선 선택할지 여부. 프론트가 방 endpoint
 *                이거나 좌표가 건물/방 폴리곤 내부일 때 true 로 보낸다. JSON 에
 *                필드가 없으면 false (기존 클라이언트 호환 → 종전 동작 유지).
 */
public record RouteCoord(double lng, double lat, int level, boolean preferIndoor) {}
