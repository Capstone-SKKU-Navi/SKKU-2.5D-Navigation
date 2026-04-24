package com.skku.nav.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skku.nav.entity.GeojsonFile;
import com.skku.nav.repository.GeojsonFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GeojsonService {

    private final GeojsonFileRepository geojsonFileRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ObjectNode getAllAsFeatureCollection() {
        List<GeojsonFile> files = geojsonFileRepository.findAll();

        ObjectNode result = objectMapper.createObjectNode();
        result.put("type", "FeatureCollection");
        ArrayNode allFeatures = result.putArray("features");

        for (GeojsonFile gf : files) {
            try {
                JsonNode root = objectMapper.readTree(gf.getContent());
                ArrayNode features = extractFeatures(root);

                for (JsonNode feature : features) {
                    ObjectNode f = feature.deepCopy();
                    ObjectNode props = f.has("properties") && f.get("properties").isObject()
                            ? (ObjectNode) f.get("properties")
                            : f.putObject("properties");

                    props.put("_building", gf.getBuilding());
                    if (gf.getLevel() != null) {
                        props.put("_level", gf.getLevel());
                    } else {
                        props.putNull("_level");
                    }
                    props.put("_featureType", gf.getFileType());

                    allFeatures.add(f);
                }
            } catch (Exception e) {
                // 파싱 실패한 레코드는 건너뜀
            }
        }

        return result;
    }

    private ArrayNode extractFeatures(JsonNode root) {
        ArrayNode out = objectMapper.createArrayNode();
        String type = root.path("type").asText("");

        if ("FeatureCollection".equals(type)) {
            JsonNode arr = root.path("features");
            if (arr.isArray()) {
                arr.forEach(out::add);
            }
        } else if ("Feature".equals(type)) {
            out.add(root);
        }
        return out;
    }
}
