package com.skku.nav.service;

import com.skku.nav.dto.EdgeDto;
import com.skku.nav.dto.NodeDto;
import com.skku.nav.dto.RouteResponseDto;
import com.skku.nav.entity.NavEdge;
import com.skku.nav.entity.NavNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Dijkstra 기반 경로 탐색 서비스.
 * 그래프는 GraphService 캐시에서 읽는다.
 */
@Service
@RequiredArgsConstructor
public class RouteService {

    private final GraphService graphService;

    /**
     * @param fromId 출발 노드 ID
     * @param toId   도착 노드 ID
     * @return 최단 경로 (노드 목록 + 엣지 목록) 또는 notFound
     */
    public RouteResponseDto findRoute(String fromId, String toId) {
        Map<String, NavNode> nodeMap = graphService.getNodeMap();
        Map<String, List<GraphService.AdjEntry>> adjacency = graphService.getAdjacency();

        if (!nodeMap.containsKey(fromId) || !nodeMap.containsKey(toId)) {
            return RouteResponseDto.notFound();
        }
        if (fromId.equals(toId)) {
            NodeDto single = NodeDto.from(nodeMap.get(fromId));
            return new RouteResponseDto(true, 0, List.of(single), List.of());
        }

        // ── Dijkstra ──────────────────────────────────────────
        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();        // nodeId → prevNodeId
        Map<String, NavEdge> edgeUsed = new HashMap<>();   // nodeId → edge used to reach it

        PriorityQueue<double[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> a[0]));

        for (String id : nodeMap.keySet()) dist.put(id, Double.MAX_VALUE);
        dist.put(fromId, 0.0);
        pq.offer(new double[]{0.0, fromId.hashCode()});
        // PQ에서 ID를 보관하기 위해 별도 Map 사용
        Map<Integer, String> hashToId = new HashMap<>();
        hashToId.put(fromId.hashCode(), fromId);

        Set<String> visited = new HashSet<>();

        while (!pq.isEmpty()) {
            double[] top = pq.poll();
            double d = top[0];
            String uid = hashToId.get((int) top[1]);
            if (uid == null || visited.contains(uid)) continue;
            visited.add(uid);
            if (uid.equals(toId)) break;

            List<GraphService.AdjEntry> neighbors = adjacency.getOrDefault(uid, List.of());
            for (GraphService.AdjEntry entry : neighbors) {
                String nid = entry.neighborId();
                if (visited.contains(nid)) continue;
                double nd = d + entry.edge().getWeight();
                if (nd < dist.getOrDefault(nid, Double.MAX_VALUE)) {
                    dist.put(nid, nd);
                    prev.put(nid, uid);
                    edgeUsed.put(nid, entry.edge());
                    int hash = System.identityHashCode(nid);
                    hashToId.put(hash, nid);
                    pq.offer(new double[]{nd, hash});
                }
            }
        }

        if (dist.getOrDefault(toId, Double.MAX_VALUE) == Double.MAX_VALUE) {
            return RouteResponseDto.notFound();
        }

        // ── 경로 복원 ──────────────────────────────────────────
        LinkedList<String> pathIds = new LinkedList<>();
        LinkedList<NavEdge> pathEdges = new LinkedList<>();
        String cur = toId;
        while (cur != null) {
            pathIds.addFirst(cur);
            NavEdge e = edgeUsed.get(cur);
            if (e != null) pathEdges.addFirst(e);
            cur = prev.get(cur);
        }

        List<NodeDto> nodePath = pathIds.stream()
                .map(id -> NodeDto.from(nodeMap.get(id)))
                .toList();
        List<EdgeDto> edgePath = pathEdges.stream()
                .map(EdgeDto::from)
                .toList();

        return new RouteResponseDto(true, dist.get(toId), nodePath, edgePath);
    }
}
