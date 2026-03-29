package com.skku.nav.dto;

/**
 * 프론트엔드 RouteEdge 인터페이스와 1:1 매핑
 * { from, to, video, videoStart, videoEnd, duration }
 */
public record RouteEdgeDto(
        String from,           // 출발 노드 ID
        String to,             // 도착 노드 ID
        String video,          // 영상 파일명 (null 이면 영상 없음)
        Long videoStart,       // 영상 시작 타임스탬프 (밀리초)
        Long videoEnd,         // 영상 종료 타임스탬프 (밀리초)
        double duration        // 영상 구간 길이 (초) — 프론트가 재생 시간 계산용으로 사용
) {}
