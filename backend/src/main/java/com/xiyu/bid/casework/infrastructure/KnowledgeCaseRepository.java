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

    /**
     * 来源五字段条件更新。WHERE 除 id 与 ACTIVE 状态外，还原子校验扫描时刻用于计算来源的
     * 关键业务数据（正文/评分项标题/来源项目 ID）未发生变化；不满足则返回 0，由调用方失败回滚整批。
     * 字符串比较用 BINARY 精确语义：库默认校对（大小写/尾随空格不敏感，如 utf8mb4_unicode_ci、
     * utf8mb4_0900_ai_ci）下普通 = 会把"业务数据已变"误判为相同。nativeQuery 是 JPQL 不支持 BINARY 所致，
     * 仍在 Repository 边界内，无新迁移、无全局锁、无版本字段。
     */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query(value = "UPDATE knowledge_case SET "
            + "source_section_title = :sourceSectionTitle, "
            + "source_section_path = :sourceSectionPath, "
            + "source_section_index = :sourceSectionIndex, "
            + "source_document_name = :sourceDocumentName, "
            + "source_page_number = :sourcePageNumber "
            + "WHERE id = :id AND status = 'ACTIVE' "
            + "AND BINARY response_text = BINARY :expectedResponseText "
            + "AND BINARY scoring_point_title = BINARY :expectedScoringPointTitle "
            + "AND source_project_id = :expectedSourceProjectId",
            nativeQuery = true)
    int updateProvenanceIfActive(
            @org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("sourceSectionTitle") String sourceSectionTitle,
            @org.springframework.data.repository.query.Param("sourceSectionPath") String sourceSectionPath,
            @org.springframework.data.repository.query.Param("sourceSectionIndex") Integer sourceSectionIndex,
            @org.springframework.data.repository.query.Param("sourceDocumentName") String sourceDocumentName,
            @org.springframework.data.repository.query.Param("sourcePageNumber") Integer sourcePageNumber,
            @org.springframework.data.repository.query.Param("expectedResponseText") String expectedResponseText,
            @org.springframework.data.repository.query.Param("expectedScoringPointTitle") String expectedScoringPointTitle,
            @org.springframework.data.repository.query.Param("expectedSourceProjectId") Long expectedSourceProjectId);
}


