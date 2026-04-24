package com.skku.nav.dto;

public record RouteEdgeDto(
        String from,
        String to,
        String video,
        Double videoStart,   // 초
        Double videoEnd,     // 초
        double duration      // 초
) {}
