package com.skku.nav.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "nav_nodes", indexes = {
        @Index(name = "idx_nav_nodes_level", columnList = "level"),
        @Index(name = "idx_nav_nodes_type", columnList = "type"),
        @Index(name = "idx_nav_nodes_building", columnList = "building")
})
@Getter
@Setter
@NoArgsConstructor
public class NavNode {

    @Id
    @Column(length = 50)
    private String id;

    @Column(length = 20, nullable = false)
    private String building = "";

    @Column(nullable = false)
    private int level;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private NodeType type;

    @Column(length = 100)
    private String label = "";

    /** 방 표시 이름 — room 타입 노드에만 사용 (예: "소프트웨어학과 실험실") */
    @Column(length = 200)
    private String name = "";

    /**
     * 방 세부 유형 — room 타입 노드에만 사용
     * 예: "lecture", "lab", "office", "restroom", "lounge" 등
     */
    @Column(name = "room_type", length = 30)
    private String roomType = "";

    /**
     * WGS84 좌표 (EPSG:4326) — PostGIS POINT(longitude latitude)
     */
    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point location;

    /** stairs: 1~4, elevator: 1~2 — 물리적 유닛 식별자 */
    @Column(name = "vertical_id")
    private Integer verticalId;

    public enum NodeType {
        corridor, room, stairs, elevator, entrance
    }
}
