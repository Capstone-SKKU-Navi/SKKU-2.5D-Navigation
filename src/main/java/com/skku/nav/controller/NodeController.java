package com.skku.nav.controller;

import com.skku.nav.dto.GraphDto;
import com.skku.nav.dto.NodeDto;
import com.skku.nav.entity.NavNode;
import com.skku.nav.repository.NavNodeRepository;
import com.skku.nav.service.GraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NodeController {

    private final NavNodeRepository nodeRepository;
    private final GraphService graphService;

    /** 전체 그래프 (노드 + 엣지) — 프론트엔드 초기 로드용 */
    @GetMapping("/graph")
    public GraphDto getGraph() {
        return graphService.getFullGraph();
    }

    /** 전체 노드 목록 (building, level 필터 가능) */
    @GetMapping("/nodes")
    public List<NodeDto> getNodes(
            @RequestParam(required = false) String building,
            @RequestParam(required = false) Integer level
    ) {
        List<NavNode> nodes;
        if (building != null && level != null) {
            nodes = nodeRepository.findByBuildingAndLevel(building, level);
        } else if (building != null) {
            nodes = nodeRepository.findByBuilding(building);
        } else if (level != null) {
            nodes = nodeRepository.findByLevel(level);
        } else {
            nodes = nodeRepository.findAll();
        }
        return nodes.stream().map(NodeDto::from).toList();
    }

    /** 단일 노드 조회 */
    @GetMapping("/nodes/{id}")
    public ResponseEntity<NodeDto> getNode(@PathVariable String id) {
        return nodeRepository.findById(id)
                .map(n -> ResponseEntity.ok(NodeDto.from(n)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** 노드 이름/번호 검색 (타입 무관) */
    @GetMapping("/nodes/search")
    public List<NodeDto> searchNodes(@RequestParam String q) {
        return nodeRepository.findByLabelContainingIgnoreCase(q)
                .stream().map(NodeDto::from).toList();
    }

    /**
     * 방 전용 검색 — 프론트엔드 apiRoute.ts searchRooms() 가 호출하는 엔드포인트.
     * room 타입 노드만 반환하며, label(방 번호)과 name(방 이름) 모두 검색한다.
     */
    @GetMapping("/rooms/search")
    public List<NodeDto> searchRooms(@RequestParam String q) {
        if (q == null || q.isBlank()) return List.of();
        return nodeRepository.searchRooms(q.trim())
                .stream().map(NodeDto::from).toList();
    }

    /** 반경 내 가장 가까운 노드 탐색 (기본 반경 100m, 최대 5개) */
    @GetMapping("/nodes/nearby")
    public List<NodeDto> getNearby(
            @RequestParam double lng,
            @RequestParam double lat,
            @RequestParam(defaultValue = "100") double radius,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return nodeRepository.findNearby(lng, lat, radius, limit)
                .stream().map(NodeDto::from).toList();
    }

    /** 그래프 캐시 수동 갱신 */
    @PostMapping("/graph/reload")
    public ResponseEntity<String> reloadGraph() {
        graphService.reload();
        return ResponseEntity.ok("Graph reloaded");
    }
}
