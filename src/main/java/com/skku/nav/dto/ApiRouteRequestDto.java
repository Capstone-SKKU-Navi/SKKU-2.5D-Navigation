package com.skku.nav.dto;

/** POST /api/route 요청 바디 */
public record ApiRouteRequestDto(RouteCoord from, RouteCoord to) {}
