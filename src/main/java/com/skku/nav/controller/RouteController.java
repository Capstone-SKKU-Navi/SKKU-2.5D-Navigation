package com.skku.nav.controller;

import com.skku.nav.dto.RouteRequestDto;
import com.skku.nav.dto.RouteResponseDto;
import com.skku.nav.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/route")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    /**
     * POST /api/route
     * Body: { "fromNodeId": "node-xxx", "toNodeId": "node-yyy" }
     */
    @PostMapping
    public RouteResponseDto findRoute(@RequestBody RouteRequestDto request) {
        return routeService.findRoute(request.fromNodeId(), request.toNodeId());
    }

    /**
     * GET /api/route?from=node-xxx&to=node-yyy
     * 프론트엔드 쿼리 파라미터 방식 호환
     */
    @GetMapping
    public RouteResponseDto findRouteGet(
            @RequestParam String from,
            @RequestParam String to
    ) {
        return routeService.findRoute(from, to);
    }
}
