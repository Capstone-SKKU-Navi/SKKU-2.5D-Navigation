package com.skku.nav.dto;

import java.util.List;

public record RouteResponseDto(
        boolean found,
        double totalDistance,
        List<NodeDto> path,
        List<EdgeDto> edges
) {
    public static RouteResponseDto notFound() {
        return new RouteResponseDto(false, 0, List.of(), List.of());
    }
}
