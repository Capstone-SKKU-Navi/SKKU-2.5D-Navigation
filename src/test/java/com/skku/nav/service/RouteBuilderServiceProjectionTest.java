package com.skku.nav.service;

import com.skku.nav.dto.ApiRouteResponseDto;
import com.skku.nav.dto.RouteCoord;
import com.skku.nav.entity.NavEdge;
import com.skku.nav.entity.NavNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 방(preferIndoor=true) endpoint 의 수선의 발 투영이 실외(outside_*) edge 를 피하고
 * 실내 corridor edge 를 선택하는지 검증.
 *
 * 시나리오 (평면 좌표로 단순화):
 *   indoor edge  I : A(0,0) ─ B(10,0)            video "eng1_c_1_ccw.mp4"
 *   outside edge O : C(0,-1) ─ D(10,-1)          video "outside_1_cw.mp4"  ← 실외 보행로
 *   connector      : B(10,0) ─ D(10,-1)          (그래프 연결용)
 *
 *   from(room) P = (5, -0.6)  →  O 까지 0.4,  I 까지 0.6  (O 가 더 가까움)
 *   to        Q = (8,  0.4)   →  I 로 투영
 *
 * 기대:
 *   preferIndoor=false → 가장 가까운 edge = O → fromProj.point 의 lat ≈ -1.0 (실외 선 위)
 *   preferIndoor=true  → 실내 우선 → I 선택  → fromProj.point 의 lat ≈  0.0 (실내 선 위)
 *
 * fromProj.point 는 응답 coordinates[1] (= [from, fromProj, ..., toProj, to]).
 */
class RouteBuilderServiceProjectionTest {

    private static final GeometryFactory GF = new GeometryFactory();

    private GraphService     graphService;
    private VideoStreamCache videoCache;
    private RouteBuilderService service;

    @BeforeEach
    void setUp() {
        graphService = mock(GraphService.class);
        videoCache   = mock(VideoStreamCache.class);
        when(videoCache.getYaw(anyString())).thenReturn(0.0);

        NavNode a = node("A", 0,  0);
        NavNode b = node("B", 10, 0);
        NavNode c = node("C", 0,  -1);
        NavNode d = node("D", 10, -1);

        NavEdge indoor    = edge("I", a, b, "eng1_c_1_ccw.mp4", "eng1_c_1_cw.mp4");
        NavEdge outside   = edge("O", c, d, "outside_1_cw.mp4",  "outside_1_ccw.mp4");
        NavEdge connector = edge("K", b, d, "eng1_c_2_ccw.mp4",  "eng1_c_2_cw.mp4");

        Map<String, NavNode> nodeMap = new HashMap<>();
        for (NavNode n : List.of(a, b, c, d)) nodeMap.put(n.getId(), n);

        List<NavEdge> edges = List.of(indoor, outside, connector);

        Map<Integer, List<NavEdge>> byLevel = Map.of(1, edges);

        Map<String, List<GraphService.AdjEntry>> adj = new HashMap<>();
        adj.put("A", List.of(new GraphService.AdjEntry("B", indoor)));
        adj.put("B", List.of(new GraphService.AdjEntry("A", indoor),
                             new GraphService.AdjEntry("D", connector)));
        adj.put("C", List.of(new GraphService.AdjEntry("D", outside)));
        adj.put("D", List.of(new GraphService.AdjEntry("C", outside),
                             new GraphService.AdjEntry("B", connector)));

        when(graphService.getNodeMap()).thenReturn(nodeMap);
        when(graphService.getEdgesByLevel()).thenReturn(byLevel);
        when(graphService.getAdjacency()).thenReturn(adj);
        when(graphService.getEdgeList()).thenReturn(edges);

        service = new RouteBuilderService(graphService, videoCache);
    }

    @Test
    void coordPin_picksNearestEdge_whichIsOutside() {
        // preferIndoor=false → 종전 동작: 가장 가까운 edge(O, 실외) 선택
        RouteCoord from = new RouteCoord(5, -0.6, 1, false);
        RouteCoord to   = new RouteCoord(8,  0.4, 1, false);

        ApiRouteResponseDto res = service.findRoute(from, to);

        assertThat(res.found()).isTrue();
        double projLat = res.route().coordinates().get(1)[1];
        assertThat(projLat).as("coord pin → 실외 edge 위로 투영").isEqualTo(-1.0);
    }

    @Test
    void room_prefersIndoorEdge_evenWhenOutsideIsCloser() {
        // preferIndoor=true → 실내 우선: O 가 더 가깝지만 I(실내) 로 투영되어야 함
        RouteCoord from = new RouteCoord(5, -0.6, 1, true);
        RouteCoord to   = new RouteCoord(8,  0.4, 1, false);

        ApiRouteResponseDto res = service.findRoute(from, to);

        assertThat(res.found()).isTrue();
        double projLat = res.route().coordinates().get(1)[1];
        assertThat(projLat).as("room → 실내 edge 위로 투영 (실외 회피)").isEqualTo(0.0);
    }

    // ── helpers ────────────────────────────────────────────────
    private static NavNode node(String id, double lng, double lat) {
        NavNode n = new NavNode();
        n.setId(id);
        n.setLevel(1);
        n.setType(NavNode.NodeType.corridor);
        n.setBuilding("eng1");
        Point p = GF.createPoint(new Coordinate(lng, lat));
        n.setLocation(p);
        return n;
    }

    private static NavEdge edge(String id, NavNode from, NavNode to, String vFwd, String vRev) {
        NavEdge e = new NavEdge();
        e.setId(id);
        e.setFromNode(from);
        e.setToNode(to);
        e.setWeight(10.0);
        e.setVideoFwd(vFwd);
        e.setVideoFwdStart(0.0);
        e.setVideoFwdEnd(10.0);
        e.setVideoRev(vRev);
        e.setVideoRevStart(0.0);
        e.setVideoRevEnd(10.0);
        return e;
    }
}
