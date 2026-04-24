package com.skku.nav.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "nav_edges", indexes = {
        @Index(name = "idx_nav_edges_from", columnList = "from_node_id"),
        @Index(name = "idx_nav_edges_to",   columnList = "to_node_id")
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

    @Column(nullable = false)
    private double weight;

    /** 건물 코드 (from_node 기준) */
    @Column(length = 20)
    private String building = "";

    /** from_node 층 */
    @Column(name = "from_level")
    private Integer fromLevel;

    /** to_node 층 */
    @Column(name = "to_level")
    private Integer toLevel;

    // ── 순방향 (from → to) 복도 영상 ────────────────────────────
    @Column(name = "video_fwd", length = 200)
    private String videoFwd;

    @Column(name = "video_fwd_start")
    private Double videoFwdStart;   // 초

    @Column(name = "video_fwd_end")
    private Double videoFwdEnd;     // 초

    // ── 순방향 계단/엘리베이터 진출 클립 ─────────────────────────
    @Column(name = "video_fwd_exit", length = 200)
    private String videoFwdExit;

    @Column(name = "video_fwd_exit_start")
    private Double videoFwdExitStart;

    @Column(name = "video_fwd_exit_end")
    private Double videoFwdExitEnd;

    // ── 역방향 (to → from) 복도 영상 ────────────────────────────
    @Column(name = "video_rev", length = 200)
    private String videoRev;

    @Column(name = "video_rev_start")
    private Double videoRevStart;   // 초

    @Column(name = "video_rev_end")
    private Double videoRevEnd;     // 초

    // ── 역방향 계단/엘리베이터 진출 클립 ─────────────────────────
    @Column(name = "video_rev_exit", length = 200)
    private String videoRevExit;

    @Column(name = "video_rev_exit_start")
    private Double videoRevExitStart;

    @Column(name = "video_rev_exit_end")
    private Double videoRevExitEnd;
}
