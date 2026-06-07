package com.xiyu.bid.documentexport.repository;

import com.xiyu.bid.documentexport.entity.DocumentExport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DocumentExportRepository extends JpaRepository<DocumentExport, Long> {

    List<DocumentExport> findByProjectIdOrderByExportedAtDesc(Long projectId);

    List<DocumentExport> findByProjectIdInOrderByExportedAtDesc(Collection<Long> projectIds);
}
