package com.xiyu.bid.documenteditor.repository;

import com.xiyu.bid.documenteditor.entity.DocumentStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 文档结构仓储接口
 */
@Repository
public interface DocumentStructureRepository extends JpaRepository<DocumentStructure, Long> {

    /**
     * 根据项目ID查找文档结构
     *
     * @param projectId 项目ID
     * @return 文档结构
     */
    Optional<DocumentStructure> findByProjectId(Long projectId);
}
