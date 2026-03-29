package com.skku.nav.dto;

import com.skku.nav.entity.NavNode;

public record NodeDto(
        String id,
        String building,
        int level,
        String type,
        String label,
        double longitude,
        double latitude,
        Long clipFwdStart,
        Long clipFwdEnd,
        Long clipRevStart,
        Long clipRevEnd
) {
    public static NodeDto from(NavNode node) {
        double lng = node.getLocation() != null ? node.getLocation().getX() : 0;
        double lat = node.getLocation() != null ? node.getLocation().getY() : 0;
        return new NodeDto(
                node.getId(),
                node.getBuilding(),
                node.getLevel(),
                node.getType().name(),
                node.getLabel(),
                lng,
                lat,
                node.getClipFwdStart(),
                node.getClipFwdEnd(),
                node.getClipRevStart(),
                node.getClipRevEnd()
        );
    }
}
