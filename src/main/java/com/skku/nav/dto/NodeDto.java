package com.skku.nav.dto;

import com.skku.nav.entity.NavNode;

public record NodeDto(
        String id,
        String building,
        int level,
        String type,
        String label,
        // room 검색용 추가 필드 — /api/rooms/search 응답에서 사용
        String ref,        // 방 번호 (label과 동일, 프론트엔드 RoomListItem.ref 와 매핑)
        String name,       // 방 표시 이름
        String roomType,   // 방 세부 유형 (lecture / lab / office / restroom 등)
        double longitude,
        double latitude,
        double[] centroid  // [longitude, latitude] — 프론트엔드 RoomListItem.centroid 와 매핑
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
                node.getLabel(),                         // ref = label
                node.getName()    != null ? node.getName()    : "",
                node.getRoomType() != null ? node.getRoomType() : "",
                lng,
                lat,
                new double[]{lng, lat}                   // centroid = 노드 좌표
        );
    }
}
