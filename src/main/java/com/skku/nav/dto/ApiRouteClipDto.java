package com.skku.nav.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 프론트엔드 ApiRouteClip 인터페이스와 1:1 매핑.
 * isExitClip 은 JavaBean 관례 충돌 방지를 위해 @JsonProperty 명시.
 */
public record ApiRouteClipDto(
        int    index,
        String videoFile,
        double videoStart,       // 초
        double videoEnd,         // 초
        double duration,         // 초
        double yaw,              // 도
        int    level,
        @JsonProperty("isExitClip") boolean isExitClip,
        int    coordStartIdx,
        int    coordEndIdx,
        double routeDistStart,   // 누적 거리 (m)
        double routeDistEnd
) {}
