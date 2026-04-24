package com.skku.nav.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skku.nav.service.GeojsonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/geojson")
@RequiredArgsConstructor
public class GeojsonController {

    private final GeojsonService geojsonService;

    /** 모든 건물 GeoJSON을 단일 FeatureCollection으로 병합 반환 */
    @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ObjectNode> getAll() {
        return ResponseEntity.ok(geojsonService.getAllAsFeatureCollection());
    }
}
