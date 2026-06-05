package com.xiyu.bid.repository;

import com.xiyu.bid.entity.Template;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 模板Repository接口
 */
@Repository
public interface TemplateRepository extends JpaRepository<Template, Long>, JpaSpecificationExecutor<Template> {

    /**
     * 根据类别查找模板（分页）
     */
    Page<Template> findByCategory(Template.Category category, Pageable pageable);

    /**
     * 根据名称查找模板（模糊查询，分页）
     */
    Page<Template> findByNameContaining(String name, Pageable pageable);

    /**
     * 根据创建者查找模板（分页）
     */
    Page<Template> findByCreatedBy(Long createdBy, Pageable pageable);

    /**
     * 根据类别和创建者查找模板（分页）
     */
    Page<Template> findByCategoryAndCreatedBy(Template.Category category, Long createdBy, Pageable pageable);

    /**
     * 统计类别的模板数量
     */
    Long countByCategory(Template.Category category);

    /**
     * 统计创建者的模板数量
     */
    Long countByCreatedBy(Long createdBy);

    /**
     * 删除用户的所有模板
     */
    void deleteByCreatedBy(Long createdBy);

    /**
     * 根据类别查找模板（限制返回数量）
     */
    List<Template> findByCategory(Template.Category category);

    /**
     * 根据创建者查找模板（限制返回数量）
     */
    List<Template> findByCreatedBy(Long createdBy);
}
