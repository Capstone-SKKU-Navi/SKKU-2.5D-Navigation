package com.skku.nav.service;

import com.skku.nav.dto.ApiRouteClipDto;
import com.skku.nav.dto.ApiRouteResponseDto;
import com.skku.nav.dto.RouteCoord;
import com.skku.nav.entity.NavEdge;
import com.skku.nav.entity.NavNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * POST /api/route 핵심 서비스.
 *
 * TypeScript localRoute.ts + graphService.ts 로직을 Java로 이식:
 *   1. 입력 좌표 → 가장 가까운 복도 edge 위의 수선의 발(perpendicular foot) 투영
 *   2. Dijkstra: 양쪽 endpoint 4가지 조합 중 최단 경로 선택
 *   3. 백트래킹 제거 → 좌표 배열 + 층 배열 조립
 *   4. 복도/계단/엘리베이터 클립 조립 (부분 타임스탬프 포함)
 */
@Service
@RequiredArgsConstructor
public class RouteBuilderService {

    private static final double WALKING_SPEED_M_PER_MIN  = 72.0;
    private static final double STAIR_CLIP_DURATION      = 4.0;   // 초
    private static final double ELEVATOR_CLIP_DURATION   = 3.0;   // 초
    private static final double MIN_COORD_GAP            = 0.000009; // ~1m 중복 제거 임계값
    private static final double EARTH_RADIUS_M           = 6_371_000.0;

    private final GraphService     graphService;
    private final VideoStreamCache videoCache;

    // ── 내부 값 타입 ─────────────────────────────────────────────

    private record EdgeProjection(
            double[] point,   // [lng, lat] — edge 위의 투영점
            String   nodeA,   // edge.fromNode.id
            String   nodeB,   // edge.toNode.id
            double   distToA, // Haversine(m): 투영점 → nodeA
            double   distToB  // Haversine(m): 투영점 → nodeB
    ) {}

    private record DijkstraResult(List<String> path, double totalWeight) {}

    private record EdgePathEntry(NavEdge edge, boolean forward, NavNode fromNode, NavNode toNode) {}

    // ── 공개 진입점 ──────────────────────────────────────────────

    public ApiRouteResponseDto findRoute(RouteCoord from, RouteCoord to) {
        Map<String, NavNode>                     nodeMap      = graphService.getNodeMap();
        Map<Integer, List<NavEdge>>              edgesByLevel = graphService.getEdgesByLevel();
        Map<String, List<GraphService.AdjEntry>> adjacency    = graphService.getAdjacency();

        List<NavEdge> fromEdges = edgesByLevel.getOrDefault(from.level(), List.of());
        List<NavEdge> toEdges   = edgesByLevel.getOrDefault(to.level(),   List.of());

        // 1. 수선의 발 투영
        EdgeProjection fromProj = projectOntoNearestEdge(from.lng(), from.lat(), nodeMap, fromEdges);
        EdgeProjection toProj   = projectOntoNearestEdge(to.lng(),   to.lat(),   nodeMap, toEdges);
        if (fromProj == null || toProj == null) return ApiRouteResponseDto.notFound();

        // 2. 같은 edge 위인지 확인
        boolean sameEdge = isSameEdge(fromProj, toProj);

        List<String> pathNodeIds;
        List<String> trimmedPath;

        if (sameEdge) {
            pathNodeIds = List.of(fromProj.nodeA(), fromProj.nodeB());
            trimmedPath = List.of();
        } else {
            // 3. 4가지 endpoint 조합 Dijkstra → 최단 경로
            DijkstraResult best = bestDijkstra(fromProj, toProj, nodeMap, adjacency);
            if (best == null) return ApiRouteResponseDto.notFound();
            pathNodeIds = best.path();
            trimmedPath = trimBacktracking(best.path(), fromProj, toProj);
        }

        // 4. 좌표 + 층 배열 조립
        List<double[]> rawCoords = new ArrayList<>();
        List<Integer>  rawLevels = new ArrayList<>();

        addCoord(rawCoords, rawLevels, new double[]{from.lng(), from.lat()}, from.level());
        addCoord(rawCoords, rawLevels, fromProj.point(), from.level());
        for (String nid : trimmedPath) {
            NavNode n = nodeMap.get(nid);
            if (n != null && n.getLocation() != null) {
                addCoord(rawCoords, rawLevels,
                        new double[]{n.getLocation().getX(), n.getLocation().getY()},
                        n.getLevel());
            }
        }
        addCoord(rawCoords, rawLevels, toProj.point(), to.level());
        addCoord(rawCoords, rawLevels, new double[]{to.lng(), to.lat()}, to.level());

        // 연속 근접점 중복 제거 (~1m)
        List<double[]> coords = new ArrayList<>();
        List<Integer>  levels = new ArrayList<>();
        dedup(rawCoords, rawLevels, coords, levels);

        // 5. Edge path 구성
        List<EdgePathEntry> edgePath = buildEdgePath(fromProj, toProj, trimmedPath, sameEdge, graphService.getEdgeList(), nodeMap);

        // 6. 누적 거리 배열
        double[] cumDist = buildCumulativeDist(coords);

        // 7. 영상 구간 좌표 인덱스 (수선의 발 투영점 위치)
        int vStartIdx = findCoordIndex(coords, fromProj.point(), false);
        int vEndIdx   = findCoordIndex(coords, toProj.point(),   true);

        // 8. 클립 조립
        List<ApiRouteClipDto> clips = buildClips(
                edgePath, coords, levels, cumDist,
                fromProj, toProj, sameEdge, from.level(), vStartIdx, vEndIdx);

        // 9. 요약
        double totalDist = cumDist[cumDist.length - 1];
        int    minutes   = (int) Math.max(1, Math.round(totalDist / WALKING_SPEED_M_PER_MIN));

        return ApiRouteResponseDto.found(
                coords, levels, totalDist, "약 " + minutes + "분",
                from.level(), to.level(),
                clips, vStartIdx, vEndIdx);
    }

    // ── 수선의 발 투영 ────────────────────────────────────────────

    private EdgeProjection projectOntoNearestEdge(
            double lng, double lat,
            Map<String, NavNode> nodeMap, List<NavEdge> edgeList) {

        EdgeProjection best     = null;
        double         bestDist = Double.MAX_VALUE;

        for (NavEdge edge : edgeList) {
            NavNode nA = nodeMap.get(edge.getFromNode().getId());
            NavNode nB = nodeMap.get(edge.getToNode().getId());
            if (nA == null || nB == null) continue;
            if (nA.getLocation() == null || nB.getLocation() == null) continue;
            if (nA.getType() == NavNode.NodeType.room || nB.getType() == NavNode.NodeType.room) continue;

            double ax = nA.getLocation().getX(), ay = nA.getLocation().getY();
            double bx = nB.getLocation().getX(), by = nB.getLocation().getY();
            double dx = bx - ax, dy = by - ay;
            double lenSq = dx * dx + dy * dy;
            if (lenSq == 0) continue;

            // 파라미터 t (0~1 클램프)
            double t = ((lng - ax) * dx + (lat - ay) * dy) / lenSq;
            t = Math.max(0, Math.min(1, t));

            double projX = ax + t * dx;
            double projY = ay + t * dy;
            double dist  = Math.sqrt((lng - projX) * (lng - projX) + (lat - projY) * (lat - projY));

            if (dist < bestDist) {
                bestDist = dist;
                double[] proj = {projX, projY};
                best = new EdgeProjection(
                        proj,
                        edge.getFromNode().getId(),
                        edge.getToNode().getId(),
                        haversineM(projX, projY, ax, ay),
                        haversineM(projX, projY, bx, by));
            }
        }
        return best;
    }

    // ── Dijkstra ─────────────────────────────────────────────────

    private DijkstraResult dijkstra(
            String startId, String endId,
            Map<String, NavNode> nodeMap,
            Map<String, List<GraphService.AdjEntry>> adjacency) {

        if (!nodeMap.containsKey(startId) || !nodeMap.containsKey(endId)) return null;

        Map<String, Double> dist    = new HashMap<>();
        Map<String, String> prev    = new HashMap<>();
        Set<String>         visited = new HashSet<>();

        for (String id : nodeMap.keySet()) dist.put(id, Double.MAX_VALUE);
        dist.put(startId, 0.0);

        PriorityQueue<AbstractMap.SimpleEntry<Double, String>> pq =
                new PriorityQueue<>(Comparator.comparingDouble(AbstractMap.SimpleEntry::getKey));
        pq.offer(new AbstractMap.SimpleEntry<>(0.0, startId));

        while (!pq.isEmpty()) {
            var top = pq.poll();
            String uid = top.getValue();
            if (visited.contains(uid)) continue;
            visited.add(uid);
            if (uid.equals(endId)) break;

            double d = top.getKey();
            for (GraphService.AdjEntry adj : adjacency.getOrDefault(uid, List.of())) {
                if (visited.contains(adj.neighborId())) continue;
                double nd = d + adj.edge().getWeight();
                if (nd < dist.getOrDefault(adj.neighborId(), Double.MAX_VALUE)) {
                    dist.put(adj.neighborId(), nd);
                    prev.put(adj.neighborId(), uid);
                    pq.offer(new AbstractMap.SimpleEntry<>(nd, adj.neighborId()));
                }
            }
        }

        if (!visited.contains(endId)) return null;

        LinkedList<String> path = new LinkedList<>();
        String cur = endId;
        while (cur != null) {
            path.addFirst(cur);
            cur = prev.get(cur);
        }
        return new DijkstraResult(new ArrayList<>(path), dist.get(endId));
    }

    /** 4가지 endpoint 조합 중 총 거리 최소인 Dijkstra 결과 반환 */
    private DijkstraResult bestDijkstra(
            EdgeProjection fromProj, EdgeProjection toProj,
            Map<String, NavNode> nodeMap,
            Map<String, List<GraphService.AdjEntry>> adjacency) {

        String[] fromEps   = {fromProj.nodeA(), fromProj.nodeB()};
        double[] fromDists  = {fromProj.distToA(), fromProj.distToB()};
        String[] toEps     = {toProj.nodeA(), toProj.nodeB()};
        double[] toDists   = {toProj.distToA(), toProj.distToB()};

        DijkstraResult best      = null;
        double         bestTotal = Double.MAX_VALUE;

        for (int fi = 0; fi < 2; fi++) {
            for (int ti = 0; ti < 2; ti++) {
                DijkstraResult r = dijkstra(fromEps[fi], toEps[ti], nodeMap, adjacency);
                if (r == null) continue;
                double total = fromDists[fi] + r.totalWeight() + toDists[ti];
                if (total < bestTotal) { bestTotal = total; best = r; }
            }
        }
        return best;
    }

    // ── 백트래킹 제거 ─────────────────────────────────────────────

    private List<String> trimBacktracking(List<String> path, EdgeProjection fromProj, EdgeProjection toProj) {
        int start = 0, end = path.size();
        if (path.size() >= 2) {
            Set<String> fromEps = Set.of(fromProj.nodeA(), fromProj.nodeB());
            if (fromEps.contains(path.get(0)) && fromEps.contains(path.get(1))) start = 1;
        }
        if (path.size() >= 2) {
            Set<String> toEps = Set.of(toProj.nodeA(), toProj.nodeB());
            if (toEps.contains(path.get(path.size() - 1)) && toEps.contains(path.get(path.size() - 2))) end = path.size() - 1;
        }
        return new ArrayList<>(path.subList(start, end));
    }

    // ── Edge path 구성 ────────────────────────────────────────────

    private List<EdgePathEntry> buildEdgePath(
            EdgeProjection fromProj, EdgeProjection toProj,
            List<String> trimmedPath, boolean sameEdge,
            List<NavEdge> edgeList, Map<String, NavNode> nodeMap) {

        List<EdgePathEntry> ep = new ArrayList<>();

        if (sameEdge || trimmedPath.isEmpty()) {
            NavEdge e = findEdge(fromProj.nodeA(), fromProj.nodeB(), edgeList);
            if (e != null) ep.add(entry(e, true, nodeMap));
            return ep;
        }

        // 시작 edge (수선의 발 → 첫 trimmed 노드 방향)
        String   firstNode = trimmedPath.get(0);
        NavEdge  startEdge = findEdge(fromProj.nodeA(), fromProj.nodeB(), edgeList);
        if (startEdge != null) {
            boolean fwd = startEdge.getToNode().getId().equals(firstNode);
            ep.add(entry(startEdge, fwd, nodeMap));
        }

        // 중간 edge들
        for (int i = 0; i < trimmedPath.size() - 1; i++) {
            NavEdge e = findEdge(trimmedPath.get(i), trimmedPath.get(i + 1), edgeList);
            if (e != null) {
                boolean fwd = e.getFromNode().getId().equals(trimmedPath.get(i));
                ep.add(entry(e, fwd, nodeMap));
            }
        }

        // 종료 edge (마지막 trimmed 노드 → 수선의 발, 시작 edge와 다를 때만)
        NavEdge endEdge = findEdge(toProj.nodeA(), toProj.nodeB(), edgeList);
        if (endEdge != null && (startEdge == null || !endEdge.getId().equals(startEdge.getId()))) {
            String  lastNode = trimmedPath.get(trimmedPath.size() - 1);
            boolean fwd      = endEdge.getFromNode().getId().equals(lastNode);
            ep.add(entry(endEdge, fwd, nodeMap));
        }
        return ep;
    }

    private static EdgePathEntry entry(NavEdge e, boolean fwd, Map<String, NavNode> nodeMap) {
        return new EdgePathEntry(e, fwd,
                nodeMap.get(e.getFromNode().getId()),
                nodeMap.get(e.getToNode().getId()));
    }

    // ── 클립 조립 ─────────────────────────────────────────────────

    private List<ApiRouteClipDto> buildClips(
            List<EdgePathEntry> edgePath,
            List<double[]> coords, List<Integer> levels, double[] cumDist,
            EdgeProjection fromProj, EdgeProjection toProj,
            boolean sameEdge, int startLevel, int vStartIdx, int vEndIdx) {

        List<ApiRouteClipDto> clips = new ArrayList<>();

        if (sameEdge) {
            ApiRouteClipDto c = buildSameEdgeClip(edgePath, fromProj, toProj,
                    cumDist, vStartIdx, vEndIdx, levels, startLevel);
            if (c != null) clips.add(c);
            return clips;
        }

        int loIdx = Math.min(vStartIdx, vEndIdx);
        int hiIdx = Math.max(vStartIdx, vEndIdx);
        Map<String, Integer> nodeToCoord = buildNodeToCoordMap(edgePath, coords, loIdx, hiIdx);

        for (int i = 0; i < edgePath.size(); i++) {
            EdgePathEntry entry    = edgePath.get(i);
            NavEdge       edge     = entry.edge();
            boolean       forward  = entry.forward();
            NavNode       fromNode = entry.fromNode();
            NavNode       toNode   = entry.toNode();
            boolean       isFirst  = i == 0;
            boolean       isLast   = i == edgePath.size() - 1;

            int csIdx = isFirst ? vStartIdx : coordStart(i, edgePath, nodeToCoord, vStartIdx);
            int ceIdx = isLast  ? vEndIdx   : coordEnd(i,   edgePath, nodeToCoord, vEndIdx);

            boolean isStairs = isStairType(fromNode) && isStairType(toNode);
            boolean isElev   = isElevType(fromNode)  && isElevType(toNode);

            if (isStairs || isElev) {
                // 실제 이동 방향 기준 노드
                NavNode fNode = forward ? fromNode : toNode;
                NavNode tNode = forward ? toNode   : fromNode;

                Integer vId = fNode.getVerticalId() != null ? fNode.getVerticalId()
                            : (tNode.getVerticalId() != null ? tNode.getVerticalId() : null);
                if (vId == null) continue;

                String building = buildingCode(fNode, tNode);
                double clipDur  = isStairs ? STAIR_CLIP_DURATION : ELEVATOR_CLIP_DURATION;

                // 연속된 같은 수직이동 edge 그룹화
                int groupEnd = i;
                for (int j = i + 1; j < edgePath.size(); j++) {
                    EdgePathEntry ej = edgePath.get(j);
                    boolean sameS = isStairType(ej.fromNode()) && isStairType(ej.toNode()) && isStairs;
                    boolean sameE = isElevType(ej.fromNode())  && isElevType(ej.toNode())  && isElev;
                    if (!sameS && !sameE) break;
                    Integer ejVId = ej.fromNode().getVerticalId() != null ? ej.fromNode().getVerticalId()
                                  : ej.toNode().getVerticalId();
                    if (!vId.equals(ejVId)) break;
                    groupEnd = j;
                }

                // 진입 클립
                String[] vids  = verticalVideoNames(isStairs, building, vId, fNode.getLevel(), tNode.getLevel());
                int entryLevel = (csIdx < levels.size()) ? levels.get(csIdx) : startLevel;
                clips.add(new ApiRouteClipDto(clips.size(), vids[0],
                        0.0, clipDur, clipDur, videoCache.getYaw(vids[0]),
                        entryLevel, false,
                        csIdx, ceIdx,
                        safe(cumDist, csIdx), safe(cumDist, ceIdx)));

                // 진출 클립 (그룹의 마지막 edge 기준)
                EdgePathEntry lastEntry = edgePath.get(groupEnd);
                NavNode lastF = lastEntry.forward() ? lastEntry.fromNode() : lastEntry.toNode();
                NavNode lastT = lastEntry.forward() ? lastEntry.toNode()   : lastEntry.fromNode();
                int lastCeIdx = (groupEnd == edgePath.size() - 1) ? vEndIdx
                              : coordEnd(groupEnd, edgePath, nodeToCoord, vEndIdx);
                String[] lastVids  = verticalVideoNames(isStairs, building, vId, lastF.getLevel(), lastT.getLevel());
                int exitLevel = (lastCeIdx < levels.size()) ? levels.get(lastCeIdx) : startLevel;
                clips.add(new ApiRouteClipDto(clips.size(), lastVids[1],
                        0.0, clipDur, clipDur, videoCache.getYaw(lastVids[1]),
                        exitLevel, true,
                        lastCeIdx, lastCeIdx,
                        safe(cumDist, lastCeIdx), safe(cumDist, lastCeIdx)));

                i = groupEnd;

            } else {
                // 복도 edge
                String video = forward ? edge.getVideoFwd()      : edge.getVideoRev();
                Double vs    = forward ? edge.getVideoFwdStart() : edge.getVideoRevStart();
                Double ve    = forward ? edge.getVideoFwdEnd()   : edge.getVideoRevEnd();
                if (video == null || vs == null || ve == null) continue;

                double clipStart = vs;
                double clipEnd   = ve;
                if (isFirst && fromProj != null) clipStart = partialTime(fromProj, forward, vs, ve);
                if (isLast  && toProj   != null) clipEnd   = partialTime(toProj,   forward, vs, ve);
                if (clipStart > clipEnd) { double tmp = clipStart; clipStart = clipEnd; clipEnd = tmp; }

                double dur = Math.max(0, clipEnd - clipStart);
                if (dur <= 0) continue;

                int clipLevel = (csIdx < levels.size()) ? levels.get(csIdx) : startLevel;
                clips.add(new ApiRouteClipDto(clips.size(), video,
                        clipStart, clipEnd, dur, videoCache.getYaw(video),
                        clipLevel, false,
                        csIdx, ceIdx,
                        safe(cumDist, csIdx), safe(cumDist, ceIdx)));
            }
        }
        return clips;
    }

    private ApiRouteClipDto buildSameEdgeClip(
            List<EdgePathEntry> edgePath,
            EdgeProjection fromProj, EdgeProjection toProj,
            double[] cumDist, int vStartIdx, int vEndIdx,
            List<Integer> levels, int startLevel) {

        if (edgePath.isEmpty()) return null;
        NavEdge edge = edgePath.get(0).edge();

        double totalDist = fromProj.distToA() + fromProj.distToB();
        if (totalDist == 0) return null;

        double  startT  = fromProj.distToA() / totalDist;
        double  endT    = toProj.distToA()   / totalDist;
        boolean forward = startT < endT;

        String video    = forward ? edge.getVideoFwd()      : edge.getVideoRev();
        Double fullStart = forward ? edge.getVideoFwdStart() : edge.getVideoRevStart();
        Double fullEnd   = forward ? edge.getVideoFwdEnd()   : edge.getVideoRevEnd();
        if (video == null || fullStart == null || fullEnd == null) return null;

        double cs = forward
                ? fullStart + startT * (fullEnd - fullStart)
                : fullStart + (1 - startT) * (fullEnd - fullStart);
        double ce = forward
                ? fullStart + endT   * (fullEnd - fullStart)
                : fullStart + (1 - endT) * (fullEnd - fullStart);
        double actStart = Math.min(cs, ce);
        double actEnd   = Math.max(cs, ce);

        int clipLevel = (vStartIdx < levels.size()) ? levels.get(vStartIdx) : startLevel;
        return new ApiRouteClipDto(0, video, actStart, actEnd, actEnd - actStart,
                videoCache.getYaw(video), clipLevel, false,
                vStartIdx, vEndIdx,
                safe(cumDist, vStartIdx), safe(cumDist, vEndIdx));
    }

    // ── 좌표 유틸리티 ─────────────────────────────────────────────

    private static void addCoord(List<double[]> coords, List<Integer> levels, double[] c, int lv) {
        coords.add(c);
        levels.add(lv);
    }

    private static void dedup(List<double[]> raw, List<Integer> rawLvs,
                               List<double[]> out, List<Integer> outLvs) {
        for (int i = 0; i < raw.size(); i++) {
            double[] cur = raw.get(i);
            if (out.isEmpty()) {
                out.add(cur); outLvs.add(rawLvs.get(i));
            } else {
                double[] prev = out.get(out.size() - 1);
                double dx = cur[0] - prev[0], dy = cur[1] - prev[1];
                if (dx * dx + dy * dy > MIN_COORD_GAP * MIN_COORD_GAP) {
                    out.add(cur); outLvs.add(rawLvs.get(i));
                }
            }
        }
    }

    private static double[] buildCumulativeDist(List<double[]> coords) {
        double[] d = new double[coords.size()];
        for (int i = 1; i < coords.size(); i++) {
            double[] a = coords.get(i - 1), b = coords.get(i);
            d[i] = d[i - 1] + haversineM(a[0], a[1], b[0], b[1]);
        }
        return d;
    }

    /** 좌표 배열에서 point에 가장 가까운 인덱스 탐색 */
    private static int findCoordIndex(List<double[]> coords, double[] point, boolean fromEnd) {
        if (point == null) return fromEnd ? coords.size() - 1 : 0;
        int    best     = fromEnd ? coords.size() - 1 : 0;
        double bestDist = Double.MAX_VALUE;
        int    start    = fromEnd ? coords.size() - 1 : 0;
        int    end      = fromEnd ? -1 : coords.size();
        int    step     = fromEnd ? -1 : 1;
        for (int i = start; i != end; i += step) {
            double[] c = coords.get(i);
            double dx = c[0] - point[0], dy = c[1] - point[1];
            double d = dx * dx + dy * dy;
            if (d < bestDist) { bestDist = d; best = i; }
        }
        return best;
    }

    /** edge path의 각 노드를 좌표 배열 인덱스에 매핑 */
    private Map<String, Integer> buildNodeToCoordMap(
            List<EdgePathEntry> edgePath, List<double[]> coords, int lo, int hi) {

        Map<String, Integer> map     = new HashMap<>();
        Set<String>          nodeIds = new LinkedHashSet<>();
        for (EdgePathEntry ep : edgePath) {
            nodeIds.add(ep.edge().getFromNode().getId());
            nodeIds.add(ep.edge().getToNode().getId());
        }
        Map<String, NavNode> nodeMap = graphService.getNodeMap();
        for (String nid : nodeIds) {
            NavNode n = nodeMap.get(nid);
            if (n == null || n.getLocation() == null) continue;
            double nx = n.getLocation().getX(), ny = n.getLocation().getY();
            int best = lo; double bestDist = Double.MAX_VALUE;
            for (int i = lo; i <= Math.min(hi, coords.size() - 1); i++) {
                double[] c = coords.get(i);
                double dx = c[0] - nx, dy = c[1] - ny;
                double d = dx * dx + dy * dy;
                if (d < bestDist) { bestDist = d; best = i; }
            }
            map.put(nid, best);
        }
        return map;
    }

    private static int coordStart(int i, List<EdgePathEntry> ep, Map<String, Integer> map, int fallback) {
        EdgePathEntry prev = ep.get(i - 1);
        String prevDest = prev.forward() ? prev.edge().getToNode().getId() : prev.edge().getFromNode().getId();
        return map.getOrDefault(prevDest, fallback);
    }

    private static int coordEnd(int i, List<EdgePathEntry> ep, Map<String, Integer> map, int fallback) {
        EdgePathEntry cur  = ep.get(i);
        String dest = cur.forward() ? cur.edge().getToNode().getId() : cur.edge().getFromNode().getId();
        return map.getOrDefault(dest, fallback);
    }

    // ── 영상 유틸리티 ─────────────────────────────────────────────

    /** 수선의 발 투영 비율로 edge 내 부분 타임스탬프 계산 */
    private static double partialTime(EdgeProjection proj, boolean forward, double fullStart, double fullEnd) {
        double total = proj.distToA() + proj.distToB();
        if (total == 0) return fullStart;
        double t = forward ? proj.distToA() / total : proj.distToB() / total;
        return fullStart + t * (fullEnd - fullStart);
    }

    /** 계단/엘리베이터 영상 파일명 계산 (프론트 verticalVideoFilename.ts 동일 규칙) */
    private static String[] verticalVideoNames(boolean isStairs, String building, int vId, int fromFloor, int toFloor) {
        String prefix = building.toLowerCase();
        if (isStairs) {
            String dir = toFloor > fromFloor ? "u" : "d";
            return new String[]{
                prefix + "_s_" + vId + "_" + fromFloor + "e" + dir + ".mp4",
                prefix + "_s_" + vId + "_" + toFloor   + "o" + dir + ".mp4"
            };
        }
        return new String[]{
            prefix + "_e_" + vId + "_" + fromFloor + "e.mp4",
            prefix + "_e_" + vId + "_" + toFloor   + "o.mp4"
        };
    }

    // ── 노드 타입 유틸리티 ────────────────────────────────────────

    private static boolean isStairType(NavNode n) { return n != null && n.getType() == NavNode.NodeType.stairs; }
    private static boolean isElevType(NavNode n)  { return n != null && n.getType() == NavNode.NodeType.elevator; }

    private static String buildingCode(NavNode a, NavNode b) {
        if (a != null && a.getBuilding() != null && !a.getBuilding().isEmpty()) return a.getBuilding();
        if (b != null && b.getBuilding() != null && !b.getBuilding().isEmpty()) return b.getBuilding();
        return "eng1";
    }

    // ── 기하 유틸리티 ─────────────────────────────────────────────

    private static double haversineM(double lng1, double lat1, double lng2, double lat2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static boolean isSameEdge(EdgeProjection a, EdgeProjection b) {
        return (a.nodeA().equals(b.nodeA()) && a.nodeB().equals(b.nodeB()))
            || (a.nodeA().equals(b.nodeB()) && a.nodeB().equals(b.nodeA()));
    }

    private static NavEdge findEdge(String nA, String nB, List<NavEdge> edgeList) {
        for (NavEdge e : edgeList) {
            String from = e.getFromNode().getId(), to = e.getToNode().getId();
            if ((from.equals(nA) && to.equals(nB)) || (from.equals(nB) && to.equals(nA))) return e;
        }
        return null;
    }

    private static double safe(double[] arr, int idx) {
        if (idx <= 0) return arr[0];
        if (idx >= arr.length) return arr[arr.length - 1];
        return arr[idx];
    }
}
