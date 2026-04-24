package com.skku.nav.controller;

import com.skku.nav.service.VideoStreamCache;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private static final long CHUNK_SIZE = 1024 * 1024; // 1 MB

    private final VideoStreamCache videoStreamCache;

    /**
     * 영상 스트리밍 — HTTP Range request 지원
     * Range 헤더 있음: 206 Partial Content
     * Range 헤더 없음: 206 Partial Content (전체, 브라우저 호환)
     */
    @GetMapping("/{filename:.+}")
    public ResponseEntity<ResourceRegion> streamVideo(
            @PathVariable String filename,
            @RequestHeader HttpHeaders headers) throws IOException {

        String filePath = videoStreamCache.getFilePath(filename)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found: " + filename));

        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File missing on server: " + filePath);
        }

        MediaType mediaType = resolveMediaType(filename);
        long contentLength = resource.contentLength();
        List<HttpRange> ranges = headers.getRange();

        ResourceRegion region;
        if (ranges.isEmpty()) {
            region = new ResourceRegion(resource, 0, contentLength);
        } else {
            HttpRange range = ranges.get(0);
            long start      = range.getRangeStart(contentLength);
            long end        = range.getRangeEnd(contentLength);
            long chunkLen   = Math.min(CHUNK_SIZE, end - start + 1);
            region = new ResourceRegion(resource, start, chunkLen);
        }

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(mediaType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(region);
    }

    /** 캐시 수동 갱신 */
    @PostMapping("/reload")
    public ResponseEntity<String> reloadCache() {
        videoStreamCache.reload();
        return ResponseEntity.ok("Video cache reloaded");
    }

    private static MediaType resolveMediaType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".mp4"))  return MediaType.parseMediaType("video/mp4");
        if (lower.endsWith(".webm")) return MediaType.parseMediaType("video/webm");
        if (lower.endsWith(".ogg"))  return MediaType.parseMediaType("video/ogg");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
