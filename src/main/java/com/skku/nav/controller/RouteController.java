package com.skku.nav.controller;

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
     * GET /api/route?from=21223&to=21517
     *
     * 프론트엔드 apiClient.ts 의 fetchRoute(from, to) 와 1:1 매핑.
     * from/to 는 방 번호(label) 문자열.
     */
    @GetMapping
    public RouteResponseDto findRoute(
            @RequestParam String from,
            @RequestParam String to
    ) {
        return routeService.findRouteByLabel(from, to);
    }
}
