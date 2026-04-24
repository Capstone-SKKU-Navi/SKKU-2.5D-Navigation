package com.skku.nav.dto;

import com.skku.nav.entity.NavEdge;

public record EdgeDto(
        String id,
        String from,
        String to,
        double weight,
        String building,
        Integer fromLevel,
        Integer toLevel,

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
    public static EdgeDto from(NavEdge e) {
        return new EdgeDto(
                e.getId(),
                e.getFromNode().getId(),
                e.getToNode().getId(),
                e.getWeight(),
                e.getBuilding(),
                e.getFromLevel(),
                e.getToLevel(),

                e.getVideoFwd(),
                e.getVideoFwdStart(),
                e.getVideoFwdEnd(),
                e.getVideoFwdExit(),
                e.getVideoFwdExitStart(),
                e.getVideoFwdExitEnd(),

                e.getVideoRev(),
                e.getVideoRevStart(),
                e.getVideoRevEnd(),
                e.getVideoRevExit(),
                e.getVideoRevExitStart(),
                e.getVideoRevExitEnd()
        );
    }
}
