package com.skku.nav.service;

import com.skku.nav.dto.GraphDto;
import com.skku.nav.dto.EdgeDto;
import com.skku.nav.dto.NodeDto;
import com.skku.nav.entity.NavEdge;
import com.skku.nav.entity.NavNode;
import com.skku.nav.repository.NavEdgeRepository;
import com.skku.nav.repository.NavNodeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 네비게이션 그래프를 메모리에 캐싱하고 인접 리스트를 관리한다.
 * DB가 변경되면 reload()를 호출해 갱신한다.
 */
@Service
@RequiredArgsConstructor
public class GraphService {

    private final NavNodeRepository nodeRepository;
    private final NavEdgeRepository edgeRepository;

    // 인접 리스트: nodeId → List<(neighborId, edge)>
    private Map<String, List<AdjEntry>> adjacency = new HashMap<>();
    private Map<String, NavNode> nodeMap = new HashMap<>();
    private List<NavEdge> edgeList = new ArrayList<>();

    public record AdjEntry(String neighborId, NavEdge edge) {}

    @PostConstruct
    @Transactional(readOnly = true)
    public void reload() {
        List<NavNode> nodes = nodeRepository.findAll();
        List<NavEdge> edges = edgeRepository.findAllWithNodes();

        Map<String, NavNode> newNodeMap = new HashMap<>();
        for (NavNode n : nodes) newNodeMap.put(n.getId(), n);

        Map<String, List<AdjEntry>> newAdj = new HashMap<>();
        for (NavNode n : nodes) newAdj.put(n.getId(), new ArrayList<>());

        // 엣지는 단방향으로 등록 (directed traversal)
        for (NavEdge e : edges) {
            String from = e.getFromNode().getId();
            String to   = e.getToNode().getId();
            newAdj.computeIfAbsent(from, k -> new ArrayList<>()).add(new AdjEntry(to, e));
        }

        this.nodeMap   = newNodeMap;
        this.adjacency = newAdj;
        this.edgeList  = edges;
    }

    public Map<String, NavNode> getNodeMap() { return nodeMap; }

    public Map<String, List<AdjEntry>> getAdjacency() { return adjacency; }

    public List<NavEdge> getEdgeList() { return edgeList; }

    @Transactional(readOnly = true)
    public GraphDto getFullGraph() {
        List<NodeDto> nodeDtos = nodeRepository.findAll().stream().map(NodeDto::from).toList();
        List<EdgeDto> edgeDtos = edgeRepository.findAllWithNodes().stream().map(EdgeDto::from).toList();
        return new GraphDto(nodeDtos, edgeDtos);
    }
}
