package com.skku.nav.repository;

import com.skku.nav.entity.GeojsonFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GeojsonFileRepository extends JpaRepository<GeojsonFile, Long> {

    List<GeojsonFile> findByBuilding(String building);

    List<GeojsonFile> findByBuildingAndLevel(String building, Integer level);

    List<GeojsonFile> findByBuildingAndFileType(String building, String fileType);
}
