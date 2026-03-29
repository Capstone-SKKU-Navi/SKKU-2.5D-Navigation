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

    /**
     * WGS84 좌표 (EPSG:4326) — PostGIS POINT(longitude latitude)
     */
    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point location;

    // ── 영상 클리핑 타임스탬프 (room 타입 노드에만 사용) ─────────────────
    // 인접한 복도/계단 엣지의 영상에서 이 방 문이 나타나는 구간을 초 단위로 기록한다.
    // 순방향(fwd): 복도 영상의 cw(시계방향) 또는 from→to 방향
    // 역방향(rev): 복도 영상의 ccw(반시계방향) 또는 to→from 방향

    /** 순방향 영상: 방 문이 등장하는 시작 타임스탬프 (밀리초) */
    private Long clipFwdStart;

    /** 순방향 영상: 방 문 통과가 완료되는 타임스탬프 (밀리초) */
    private Long clipFwdEnd;

    /** 역방향 영상: 방 문이 등장하는 시작 타임스탬프 (밀리초) */
    private Long clipRevStart;

    /** 역방향 영상: 방 문 통과가 완료되는 타임스탬프 (밀리초) */
    private Long clipRevEnd;

    public enum NodeType {
        corridor, room, stairs, elevator, entrance
    }
}
