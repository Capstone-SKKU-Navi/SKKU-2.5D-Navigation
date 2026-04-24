package com.skku.nav.controller;

import com.skku.nav.dto.ApiRouteRequestDto;
import com.skku.nav.dto.ApiRouteResponseDto;
import com.skku.nav.dto.RouteResponseDto;
import com.skku.nav.service.RouteBuilderService;
import com.skku.nav.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/route")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService        routeService;
    private final RouteBuilderService routeBuilderService;

    /**
     * POST /api/route
     * 프론트엔드 apiRoute.ts findRoute() 와 1:1 매핑.
     * { from: {lng, lat, level}, to: {lng, lat, level} } → ApiRouteResponseDto
     */
    @PostMapping
    public ApiRouteResponseDto findRouteByCoord(@RequestBody ApiRouteRequestDto req) {
        return routeBuilderService.findRoute(req.from(), req.to());
    }

    /**
     * GET /api/route?from=21223&to=21517 (레거시, 방 번호 기반)
     */
    @GetMapping
    public RouteResponseDto findRouteByLabel(
            @RequestParam String from,
            @RequestParam String to) {
        return routeService.findRouteByLabel(from, to);
    }
}
