package com.xiyu.bid.documents.repository;

import com.xiyu.bid.documents.entity.AssemblyTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文档组装模板数据访问接口
 */
@Repository
public interface AssemblyTemplateRepository extends JpaRepository<AssemblyTemplate, Long> {

    /**
     * 根据分类查询模板列表
     */
    List<AssemblyTemplate> findByCategory(String category);

    /**
     * 根据创建人查询模板列表
     */
    List<AssemblyTemplate> findByCreatedBy(Long createdBy);
}
