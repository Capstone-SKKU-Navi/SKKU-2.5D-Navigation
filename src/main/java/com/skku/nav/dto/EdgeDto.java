package com.skku.nav.dto;

import com.skku.nav.entity.NavEdge;

public record EdgeDto(
        String id,
        String from,
        String to,
        double weight,
        String videoFwd,
        Double videoFwdStart,
        Double videoFwdEnd,
        String videoFwdExit,
        Double videoFwdExitStart,
        Double videoFwdExitEnd,
        String videoRev,
        Double videoRevStart,
        Double videoRevEnd,
        String videoRevExit,
        Double videoRevExitStart,
        Double videoRevExitEnd
) {
    public static EdgeDto from(NavEdge edge) {
        return new EdgeDto(
                edge.getId(),
                edge.getFromNode().getId(),
                edge.getToNode().getId(),
                edge.getWeight(),
                edge.getVideoFwd(),
                edge.getVideoFwdStart(),
                edge.getVideoFwdEnd(),
                edge.getVideoFwdExit(),
                edge.getVideoFwdExitStart(),
                edge.getVideoFwdExitEnd(),
                edge.getVideoRev(),
                edge.getVideoRevStart(),
                edge.getVideoRevEnd(),
                edge.getVideoRevExit(),
                edge.getVideoRevExitStart(),
                edge.getVideoRevExitEnd()
        );
    }
}
