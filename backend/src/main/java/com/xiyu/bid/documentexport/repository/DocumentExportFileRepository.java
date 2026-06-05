package com.xiyu.bid.documentexport.repository;

import com.xiyu.bid.documentexport.entity.DocumentExportFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentExportFileRepository extends JpaRepository<DocumentExportFile, Long> {

    Optional<DocumentExportFile> findByExportId(Long exportId);
}
