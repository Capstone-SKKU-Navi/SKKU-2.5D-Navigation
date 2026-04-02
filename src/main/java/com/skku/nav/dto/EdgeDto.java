package com.skku.nav.dto;

import com.skku.nav.entity.NavEdge;

public record EdgeDto(
        String id,
        String from,
        String to,
        double weight,

        String video,
        Long videoStart,
        Long videoEnd,

        String videoExit,
        Long videoExitStart,
        Long videoExitEnd,

        Long clipStart,
        Long clipEnd
) {
    public static EdgeDto from(NavEdge edge) {
        return new EdgeDto(
                edge.getId(),
                edge.getFromNode().getId(),
                edge.getToNode().getId(),
                edge.getWeight(),

                edge.getVideo(),
                edge.getVideoStart(),
                edge.getVideoEnd(),

                edge.getVideoExit(),
                edge.getVideoExitStart(),
                edge.getVideoExitEnd(),

                edge.getClipStart(),
                edge.getClipEnd()
        );
    }
}
