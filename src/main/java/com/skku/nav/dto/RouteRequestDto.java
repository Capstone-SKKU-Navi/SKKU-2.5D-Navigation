package com.skku.nav.dto;

public record RouteRequestDto(
        String fromNodeId,
        String toNodeId
) {}
