package com.skku.nav.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * POST /api/route 응답.
 * 프론트엔드 ApiRouteResponse 인터페이스와 1:1 매핑.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiRouteResponseDto(
        boolean            found,
        RouteSection       route,
        WalkthroughSection walkthrough,
        String             error
) {
    public record RouteSection(
            List<double[]> coordinates,   // [[lng, lat], ...]
            List<Integer>  levels,        // coordinates 와 1:1 대응
            double         totalDistance, // m
            String         estimatedTime,
            int            startLevel,
            int            endLevel
    ) {}

    public record WalkthroughSection(
            List<ApiRouteClipDto> clips,
            int videoStartCoordIdx,
            int videoEndCoordIdx
    ) {}

    public static ApiRouteResponseDto notFound() {
        return new ApiRouteResponseDto(false, null, null, "경로를 찾을 수 없습니다");
    }

    public static ApiRouteResponseDto found(
            List<double[]> coords, List<Integer> levels,
            double totalDist, String estimatedTime, int startLevel, int endLevel,
            List<ApiRouteClipDto> clips, int vStartIdx, int vEndIdx) {
        return new ApiRouteResponseDto(
                true,
                new RouteSection(coords, levels, totalDist, estimatedTime, startLevel, endLevel),
                new WalkthroughSection(clips, vStartIdx, vEndIdx),
                null);
    }
}
