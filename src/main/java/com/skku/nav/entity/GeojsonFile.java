package com.skku.nav.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "geojson_files", indexes = {
        @Index(name = "idx_geojson_building_level", columnList = "building, level"),
        @Index(name = "idx_geojson_file_type",      columnList = "file_type")
})
@Getter
@Setter
@NoArgsConstructor
public class GeojsonFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, nullable = false)
    private String building;

    /** NULL = 건물 전체(outline) */
    private Integer level;

    /** outline | room | wall | collider */
    @Column(name = "file_type", length = 30, nullable = false)
    private String fileType;

    /** GeoJSON FeatureCollection 원문 */
    @Column(columnDefinition = "jsonb", nullable = false)
    private String content;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
