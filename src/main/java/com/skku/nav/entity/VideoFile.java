package com.skku.nav.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "video_files", indexes = {
        @Index(name = "idx_video_file_name", columnList = "file_name")
})
@Getter
@Setter
@NoArgsConstructor
public class VideoFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 영상 파일명 (예: eng1_c_F1_1_cw.mp4) */
    @Column(name = "file_name", length = 200, nullable = false, unique = true)
    private String fileName;

    /** 서버 절대경로 — Spring ResourceRegion 생성에 사용 */
    @Column(name = "file_path", length = 500, nullable = false)
    private String filePath;

    /** 카메라 방위각(도) — 복도 영상용 */
    private Double yaw;

    @Column(name = "size_bytes")
    private Long sizeBytes;
}
