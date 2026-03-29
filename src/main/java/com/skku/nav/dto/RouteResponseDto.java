package com.skku.nav.dto;

import java.util.List;

/**
 * 프론트엔드 RouteResponse 인터페이스와 1:1 매핑
 * apiClient.ts fetchRoute() 의 반환 타입
 */
public record RouteResponseDto(
        boolean found,
        List<String> path,            // 경로 상 노드 ID 목록
        List<RouteEdgeDto> edges,     // 경로 상 엣지 (영상 정보 포함)
        double totalDistance,         // 총 거리 (미터)
        String estimatedTime          // 예상 소요 시간 (예: "약 3분")
) {
    public static RouteResponseDto notFound() {
        return new RouteResponseDto(false, List.of(), List.of(), 0, "-");
    }
}
