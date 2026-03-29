package com.skku.nav.repository;

import com.skku.nav.entity.NavEdge;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NavEdgeRepository extends JpaRepository<NavEdge, String> {

    /** 그래프 전체 로드 시 N+1 방지 */
    @EntityGraph(attributePaths = {"fromNode", "toNode"})
    @Query("SELECT e FROM NavEdge e")
    List<NavEdge> findAllWithNodes();

    @EntityGraph(attributePaths = {"fromNode", "toNode"})
    @Query("SELECT e FROM NavEdge e WHERE e.fromNode.id = :nodeId OR e.toNode.id = :nodeId")
    List<NavEdge> findByNodeId(@Param("nodeId") String nodeId);
}
