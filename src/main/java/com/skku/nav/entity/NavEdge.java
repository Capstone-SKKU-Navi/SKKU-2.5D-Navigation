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
    private Long videoFwdStart;   // 밀리초
    private Long videoFwdEnd;     // 밀리초

    /** 계단/엘리베이터 진출 클립 (순방향) */
    @Column(length = 200)
    private String videoFwdExit;
    private Long videoFwdExitStart;  // 밀리초
    private Long videoFwdExitEnd;    // 밀리초

    // ── 역방향 (to → from) 비디오 ──────────────────────────────
    @Column(length = 200)
    private String videoRev;
    private Long videoRevStart;   // 밀리초
    private Long videoRevEnd;     // 밀리초

    /** 계단/엘리베이터 진출 클립 (역방향) */
    @Column(length = 200)
    private String videoRevExit;
    private Long videoRevExitStart;  // 밀리초
    private Long videoRevExitEnd;    // 밀리초
}
