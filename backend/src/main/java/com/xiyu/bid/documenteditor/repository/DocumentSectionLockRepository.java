package com.xiyu.bid.documenteditor.repository;

import com.xiyu.bid.documenteditor.entity.DocumentSectionLock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentSectionLockRepository extends JpaRepository<DocumentSectionLock, Long> {

    Optional<DocumentSectionLock> findBySectionId(Long sectionId);

    List<DocumentSectionLock> findBySectionIdIn(Collection<Long> sectionIds);
}
