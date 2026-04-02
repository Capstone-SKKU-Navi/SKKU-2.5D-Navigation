package com.skku.nav.service;

import com.skku.nav.dto.RouteEdgeDto;
import com.skku.nav.dto.RouteResponseDto;
import com.skku.nav.entity.NavEdge;
import com.skku.nav.entity.NavNode;
import com.skku.nav.repository.NavNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 방 번호(label) 기반 경로 탐색 서비스.
 *
 * 입력: fromRoomLabel ("21223"), toRoomLabel ("21517")
 * 처리: label → 노드 조회 → Dijkstra → 경로 반환
 * 출력: 프론트엔드 RouteResponse 형식 (path, edges, totalDistance, estimatedTime)
 */
@Service
@RequiredArgsConstructor
public class RouteService {

    private static final double WALKING_SPEED_M_PER_MIN = 72.0; // 분당 72m

    private final GraphService graphService;
    private final NavNodeRepository nodeRepository;

    /**
     * 방 번호로 경로를 탐색한다.
     *
     * @param fromLabel 출발 방 번호 (예: "21223")
     * @param toLabel   도착 방 번호 (예: "21517")
     */
    public RouteResponseDto findRouteByLabel(String fromLabel, String toLabel) {
        List<NavNode> fromNodes = nodeRepository.findByLabelContainingIgnoreCase(fromLabel);
        List<NavNode> toNodes   = nodeRepository.findByLabelContainingIgnoreCase(toLabel);

        if (fromNodes.isEmpty() || toNodes.isEmpty()) {
            return RouteResponseDto.notFound();
        }

        // 정확히 일치하는 노드 우선 선택
        NavNode fromNode = fromNodes.stream()
                .filter(n -> n.getLabel().equals(fromLabel)).findFirst()
                .orElse(fromNodes.get(0));
        NavNode toNode = toNodes.stream()
                .filter(n -> n.getLabel().equals(toLabel)).findFirst()
                .orElse(toNodes.get(0));

        return findRoute(fromNode.getId(), toNode.getId());
    }

    /**
     * 노드 ID로 경로를 탐색한다 (내부용).
     */
    public RouteResponseDto findRoute(String fromId, String toId) {
        Map<String, NavNode> nodeMap   = graphService.getNodeMap();
        Map<String, List<GraphService.AdjEntry>> adjacency = graphService.getAdjacency();

        if (!nodeMap.containsKey(fromId) || !nodeMap.containsKey(toId)) {
            return RouteResponseDto.notFound();
        }
        if (fromId.equals(toId)) {
            return new RouteResponseDto(true, List.of(fromId), List.of(), 0, "0분");
        }

        // ── Dijkstra ────────────────────────────────────────────
        Map<String, Double>  dist     = new HashMap<>();
        Map<String, String>  prev     = new HashMap<>();
        Map<String, NavEdge> edgeUsed = new HashMap<>();

        for (String id : nodeMap.keySet()) dist.put(id, Double.MAX_VALUE);
        dist.put(fromId, 0.0);

        // PQ: [cost, nodeId]
        PriorityQueue<AbstractMap.SimpleEntry<Double, String>> pq =
                new PriorityQueue<>(Comparator.comparingDouble(AbstractMap.SimpleEntry::getKey));
        pq.offer(new AbstractMap.SimpleEntry<>(0.0, fromId));

        Set<String> visited = new HashSet<>();

        while (!pq.isEmpty()) {
            var top = pq.poll();
            String uid = top.getValue();
            if (visited.contains(uid)) continue;
            visited.add(uid);
            if (uid.equals(toId)) break;

            double d = top.getKey();
            for (GraphService.AdjEntry entry : adjacency.getOrDefault(uid, List.of())) {
                String nid = entry.neighborId();
                if (visited.contains(nid)) continue;
                double nd = d + entry.edge().getWeight();
                if (nd < dist.getOrDefault(nid, Double.MAX_VALUE)) {
                    dist.put(nid, nd);
                    prev.put(nid, uid);
                    edgeUsed.put(nid, entry.edge());
                    pq.offer(new AbstractMap.SimpleEntry<>(nd, nid));
                }
            }
        }

        if (dist.getOrDefault(toId, Double.MAX_VALUE) == Double.MAX_VALUE) {
            return RouteResponseDto.notFound();
        }

        // ── 경로 복원 ─────────────────────────────────────────────
        LinkedList<String>   pathIds   = new LinkedList<>();
        LinkedList<NavEdge>  pathEdges = new LinkedList<>();
        String cur = toId;
        while (cur != null) {
            pathIds.addFirst(cur);
            NavEdge e = edgeUsed.get(cur);
            if (e != null) pathEdges.addFirst(e);
            cur = prev.get(cur);
        }

        // ── RouteEdgeDto 변환 ─────────────────────────────────────
        // 방향 판별: pathIds 순서와 edge의 from/to 방향 비교
        List<String> pathList = new ArrayList<>(pathIds);
        List<RouteEdgeDto> routeEdges = new ArrayList<>();

        for (int i = 0; i < pathEdges.size(); i++) {
            NavEdge e       = pathEdges.get(i);
            String  pathFrom = pathList.get(i);
            boolean forward  = e.getFromNode().getId().equals(pathFrom);

            String video      = e.getVideo();
            Long   videoStart = (e.getClipStart() != null) ? e.getClipStart() : e.getVideoStart();
            Long   videoEnd   = (e.getClipEnd() != null) ? e.getClipEnd() : e.getVideoEnd();

            double durationSec = (videoStart != null && videoEnd != null)
                    ? (videoEnd - videoStart) / 1000.0 : 0;

            routeEdges.add(new RouteEdgeDto(
                    pathList.get(i), pathList.get(i + 1),
                    video, videoStart, videoEnd, durationSec
            ));
        }

        // ── 예상 시간 계산 ────────────────────────────────────────
        double totalDist = dist.get(toId);
        int minutes = (int) Math.max(1, Math.round(totalDist / WALKING_SPEED_M_PER_MIN));
        String estimatedTime = "약 " + minutes + "분";

        return new RouteResponseDto(true, pathList, routeEdges, totalDist, estimatedTime);
    }

}
