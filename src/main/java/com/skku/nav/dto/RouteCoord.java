package com.skku.nav.dto;

/** POST /api/route 요청의 출발/도착 좌표 */
public record RouteCoord(double lng, double lat, int level) {}
