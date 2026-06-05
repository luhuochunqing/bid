package com.xiyu.bid.documenteditor.repository;

import com.xiyu.bid.documenteditor.entity.DocumentSectionAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentSectionAssignmentRepository extends JpaRepository<DocumentSectionAssignment, Long> {

    Optional<DocumentSectionAssignment> findBySectionId(Long sectionId);

    List<DocumentSectionAssignment> findBySectionIdIn(Collection<Long> sectionIds);
}
