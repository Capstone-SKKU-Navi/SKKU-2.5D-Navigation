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
 * NOTE: 이 구현은 임시 스텁입니다.
 * 7단계에서 좌표→좌표 기반 + 수선의 발 투영 + 클립 조립 로직으로 전면 교체됩니다.
 */
@Service
@RequiredArgsConstructor
public class RouteService {

    private static final double WALKING_SPEED_M_PER_MIN = 72.0;

    private final GraphService graphService;
    private final NavNodeRepository nodeRepository;

    public RouteResponseDto findRouteByLabel(String fromLabel, String toLabel) {
        List<NavNode> fromNodes = nodeRepository.findByLabelContainingIgnoreCase(fromLabel);
        List<NavNode> toNodes   = nodeRepository.findByLabelContainingIgnoreCase(toLabel);

        if (fromNodes.isEmpty() || toNodes.isEmpty()) {
            return RouteResponseDto.notFound();
        }

        NavNode fromNode = fromNodes.stream()
                .filter(n -> n.getLabel().equals(fromLabel)).findFirst()
                .orElse(fromNodes.get(0));
        NavNode toNode = toNodes.stream()
                .filter(n -> n.getLabel().equals(toLabel)).findFirst()
                .orElse(toNodes.get(0));

        return findRoute(fromNode.getId(), toNode.getId());
    }

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
        List<String> pathList = new ArrayList<>(pathIds);
        List<RouteEdgeDto> routeEdges = new ArrayList<>();

        for (int i = 0; i < pathEdges.size(); i++) {
            NavEdge e        = pathEdges.get(i);
            String  pathFrom = pathList.get(i);
            boolean forward  = e.getFromNode().getId().equals(pathFrom);

            String video      = forward ? e.getVideoFwd()      : e.getVideoRev();
            Double videoStart = forward ? e.getVideoFwdStart() : e.getVideoRevStart();
            Double videoEnd   = forward ? e.getVideoFwdEnd()   : e.getVideoRevEnd();

            double durationSec = (videoStart != null && videoEnd != null)
                    ? videoEnd - videoStart : 0;

            routeEdges.add(new RouteEdgeDto(
                    pathList.get(i), pathList.get(i + 1),
                    video, videoStart, videoEnd, durationSec
            ));
        }

        double totalDist = dist.get(toId);
        int minutes = (int) Math.max(1, Math.round(totalDist / WALKING_SPEED_M_PER_MIN));

        return new RouteResponseDto(true, pathList, routeEdges, totalDist, "약 " + minutes + "분");
    }
}
