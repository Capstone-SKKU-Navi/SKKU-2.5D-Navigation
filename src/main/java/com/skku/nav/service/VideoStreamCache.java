package com.skku.nav.service;

import com.skku.nav.entity.VideoFile;
import com.skku.nav.repository.VideoFileRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoStreamCache {

    private final VideoFileRepository videoFileRepository;

    /** fileName → filePath (서버 절대경로) */
    private volatile Map<String, String> pathMap = Map.of();
    /** fileName → yaw (도, 없으면 0.0) */
    private volatile Map<String, Double> yawMap  = Map.of();

    @PostConstruct
    public void init() {
        reload();
    }

    public void reload() {
        Map<String, String> paths = new ConcurrentHashMap<>();
        Map<String, Double> yaws  = new ConcurrentHashMap<>();
        for (VideoFile vf : videoFileRepository.findAll()) {
            paths.put(vf.getFileName(), vf.getFilePath());
            if (vf.getYaw() != null) yaws.put(vf.getFileName(), vf.getYaw());
        }
        pathMap = paths;
        yawMap  = yaws;
        log.info("VideoStreamCache loaded {} entries", paths.size());
    }

    public Optional<String> getFilePath(String fileName) {
        return Optional.ofNullable(pathMap.get(fileName));
    }

    /** yaw가 DB에 없으면 0.0 반환 */
    public double getYaw(String fileName) {
        return yawMap.getOrDefault(fileName, 0.0);
    }
}
