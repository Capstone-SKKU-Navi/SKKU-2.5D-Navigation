package com.skku.nav.repository;

import com.skku.nav.entity.VideoFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VideoFileRepository extends JpaRepository<VideoFile, Long> {

    Optional<VideoFile> findByFileName(String fileName);
}
