package com.xiyu.bid.casework.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeCaseRepository extends JpaRepository<KnowledgeCase, Long>, JpaSpecificationExecutor<KnowledgeCase> {

    List<KnowledgeCase> findBySourceProjectId(Long sourceProjectId);

    List<KnowledgeCase> findByStatus(String status);

    boolean existsBySourceProjectIdAndScoringPointTitle(Long sourceProjectId, String scoringPointTitle);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("UPDATE KnowledgeCase k SET "
            + "k.sourceSectionTitle = :sourceSectionTitle, "
            + "k.sourceSectionPath = :sourceSectionPath, "
            + "k.sourceSectionIndex = :sourceSectionIndex, "
            + "k.sourceDocumentName = :sourceDocumentName, "
            + "k.sourcePageNumber = :sourcePageNumber "
            + "WHERE k.id = :id AND k.status = 'ACTIVE'")
    int updateProvenanceIfActive(
            @org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("sourceSectionTitle") String sourceSectionTitle,
            @org.springframework.data.repository.query.Param("sourceSectionPath") String sourceSectionPath,
            @org.springframework.data.repository.query.Param("sourceSectionIndex") Integer sourceSectionIndex,
            @org.springframework.data.repository.query.Param("sourceDocumentName") String sourceDocumentName,
            @org.springframework.data.repository.query.Param("sourcePageNumber") Integer sourcePageNumber);
}


