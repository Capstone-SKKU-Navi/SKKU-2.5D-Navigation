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

            String video      = forward ? e.getVideoFwd()      : e.getVideoRev();
            Long   videoStart = forward ? e.getVideoFwdStart()  : e.getVideoRevStart();
            Long   videoEnd   = forward ? e.getVideoFwdEnd()    : e.getVideoRevEnd();

            // 방 노드 클리핑 적용
            NavNode fromRoomNode = nodeMap.get(pathList.get(i));
            NavNode toRoomNode   = nodeMap.get(pathList.get(i + 1));
            videoStart = applyRoomClip(fromRoomNode, forward, videoStart, true);
            videoEnd   = applyRoomClip(toRoomNode,   forward, videoEnd,   false);

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

    /**
     * 방 노드의 clip 타임스탬프로 영상 구간을 보정한다.
     *
     * @param node      방 노드 (room 타입이 아니면 그대로 반환)
     * @param forward   순방향 여부
     * @param fallback  방 노드 clip 값이 없을 때 사용할 기본값
     * @param isStart   시작(true) 또는 종료(false) 타임스탬프 여부
     */
    private Long applyRoomClip(NavNode node, boolean forward, Long fallback, boolean isStart) {
        if (node == null || node.getType() != NavNode.NodeType.room) return fallback;
        Long clip = isStart
                ? (forward ? node.getClipFwdStart() : node.getClipRevStart())
                : (forward ? node.getClipFwdEnd()   : node.getClipRevEnd());
        return clip != null ? clip : fallback;
    }
}
