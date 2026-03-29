package com.skku.nav.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "nav_edges", indexes = {
        @Index(name = "idx_nav_edges_from", columnList = "from_node_id"),
        @Index(name = "idx_nav_edges_to", columnList = "to_node_id")
})
@Getter
@Setter
@NoArgsConstructor
public class NavEdge {

    @Id
    @Column(length = 120)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_node_id", nullable = false)
    private NavNode fromNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_node_id", nullable = false)
    private NavNode toNode;

    /** 거리(미터) — 경로 탐색 비용 */
    @Column(nullable = false)
    private double weight;

    // ── 순방향 (from → to) 비디오 ──────────────────────────────
    @Column(length = 200)
    private String videoFwd;
    private Double videoFwdStart;
    private Double videoFwdEnd;

    /** 계단/엘리베이터 진출 클립 (순방향) */
    @Column(length = 200)
    private String videoFwdExit;
    private Double videoFwdExitStart;
    private Double videoFwdExitEnd;

    // ── 역방향 (to → from) 비디오 ──────────────────────────────
    @Column(length = 200)
    private String videoRev;
    private Double videoRevStart;
    private Double videoRevEnd;

    /** 계단/엘리베이터 진출 클립 (역방향) */
    @Column(length = 200)
    private String videoRevExit;
    private Double videoRevExitStart;
    private Double videoRevExitEnd;
}
