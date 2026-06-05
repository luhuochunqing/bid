package com.xiyu.bid.documenteditor.repository;

import com.xiyu.bid.documenteditor.entity.DocumentSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文档章节仓储接口
 */
@Repository
public interface DocumentSectionRepository extends JpaRepository<DocumentSection, Long> {

    /**
     * 根据文档结构ID查找所有章节
     *
     * @param structureId 文档结构ID
     * @return 章节列表
     */
    List<DocumentSection> findByStructureId(Long structureId);

    /**
     * 根据父章节ID查找所有子章节
     *
     * @param parentId 父章节ID
     * @return 子章节列表
     */
    List<DocumentSection> findByParentId(Long parentId);
}
