package com.skku.nav.repository;

import com.skku.nav.entity.NavNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NavNodeRepository extends JpaRepository<NavNode, String> {

    List<NavNode> findByBuilding(String building);

    List<NavNode> findByBuildingAndLevel(String building, int level);

    List<NavNode> findByLevel(int level);

    /** 방 이름/번호로 검색 (대소문자 무시) */
    List<NavNode> findByLabelContainingIgnoreCase(String label);

    /**
     * PostGIS ST_DWithin — 반경 내 가장 가까운 노드 탐색
     * distance 단위: 미터 (geography 캐스트 사용)
     */
    @Query(value = """
            SELECT * FROM nav_nodes
            WHERE ST_DWithin(
                location::geography,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                :distanceMeters
            )
            ORDER BY ST_Distance(
                location::geography,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
            )
            LIMIT :limit
            """, nativeQuery = true)
    List<NavNode> findNearby(
            @Param("lng") double lng,
            @Param("lat") double lat,
            @Param("distanceMeters") double distanceMeters,
            @Param("limit") int limit
    );
}
