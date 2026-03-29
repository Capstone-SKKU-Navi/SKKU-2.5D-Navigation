package com.skku.nav.dto;

import java.util.List;

public record GraphDto(
        List<NodeDto> nodes,
        List<EdgeDto> edges
) {}
