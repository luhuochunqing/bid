package com.xiyu.bid.documents.repository;

import com.xiyu.bid.documents.entity.DocumentAssembly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文档组装记录数据访问接口
 */
@Repository
public interface DocumentAssemblyRepository extends JpaRepository<DocumentAssembly, Long> {

    /**
     * 根据项目ID查询组装记录列表
     */
    List<DocumentAssembly> findByProjectId(Long projectId);

    /**
     * 根据项目ID和模板ID查询组装记录
     */
    List<DocumentAssembly> findByProjectIdAndTemplateId(Long projectId, Long templateId);

    /**
     * 根据模板ID查询所有使用记录
     */
    List<DocumentAssembly> findByTemplateId(Long templateId);
}
